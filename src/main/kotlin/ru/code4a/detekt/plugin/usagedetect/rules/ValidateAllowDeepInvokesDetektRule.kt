package ru.code4a.detekt.plugin.usagedetect.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Yaml
import org.jetbrains.kotlin.backend.jvm.ir.psiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.getImplementedFunctions
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.getResolvedFunctionDescriptor
import ru.code4a.detekt.plugin.usagedetect.rules.filter.FilterConfig
import ru.code4a.detekt.plugin.usagedetect.rules.filter.FilterExpressionConfig
import ru.code4a.detekt.plugin.usagedetect.rules.filter.performPass
import ru.code4a.detekt.plugin.usagedetect.rules.filter.tryMergeConfigs

@RequiresTypeResolution
class ValidateAllowDeepInvokesDetektRule(
  config: Config
) : Rule(config) {
  override val issue =
    Issue(
      javaClass.simpleName,
      Severity.CodeSmell,
      "Custom Rule",
      Debt.FIVE_MINS
    )

  private val configYaml: String by config(
    defaultValue = ""
  )

  private val ruleConfig by lazy {
    Yaml.decodeFromString(RuleConfig.serializer(), configYaml)
  }

  private val processedPsiElementSet = mutableSetOf<PsiElement>()

  override fun visitNamedFunction(ktNamedFunction: KtNamedFunction) {
    super.visitNamedFunction(ktNamedFunction)

    val functionDescriptor = ktNamedFunction.getResolvedFunctionDescriptor(bindingContext) ?: return

    val filteredRules =
      ruleConfig.rootRules.filter { rootRule ->
        rootRule.visitFilter.performPassRoot(functionDescriptor, bindingContext) is FilterConfig.PassResult.Passed
      }

    if (filteredRules.isNotEmpty()) {
      callChain(ktNamedFunction, filteredRules, currentCallStack = listOf(ktNamedFunction.psiOrParent))
    }
  }

  override fun visitProperty(property: KtProperty) {
    super.visitProperty(property)

    val filteredRules =
      ruleConfig.rootRules.filter { rootRule ->
        rootRule.visitFilter.performPassRoot(property, bindingContext) is FilterConfig.PassResult.Passed
      }

    if (filteredRules.isNotEmpty()) {
      val getterExpression =
        if (property.delegate?.expression != null) {
          property.delegate?.expression
        } else if (property.getter?.bodyExpression != null) {
          property.getter?.bodyExpression
        } else if (property.setter?.bodyExpression != null) {
          property.setter?.bodyExpression
        } else {
          null
        }

      if (getterExpression != null) {
        if (filteredRules.isNotEmpty()) {
          callChain(getterExpression, filteredRules, currentCallStack = listOf(getterExpression.psiOrParent))
        }
      }
    }
  }

  private fun callChain(
    psiElement: PsiElement,
    rules: List<RootRule>,
    currentCallStack: List<PsiElement>
  ) {
    if (processedPsiElementSet.contains(psiElement)) {
      return
    }

    processedPsiElementSet.add(psiElement)

    psiElement.accept(
      object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          super.visitReferenceExpression(expression)

          val resolvedCall = expression.getResolvedCall(bindingContext)

          val resultingDescriptor = resolvedCall?.resultingDescriptor

          val functionDescriptor =
            if (resultingDescriptor is SyntheticJavaPropertyDescriptor) {
              resultingDescriptor.getMethod
            } else if (resultingDescriptor is PropertyDescriptor) {
              resultingDescriptor.getter
            } else {
              null
            }

          if (functionDescriptor != null) {
            validateFunctionDescription(
              functionDescriptor = functionDescriptor,
              rules = rules,
              newCallStack = currentCallStack + expression
            )
          }
        }

        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          val resolvedCall = expression.getResolvedCall(bindingContext) ?: return
          val functionDescriptor = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return

          validateFunctionDescription(
            functionDescriptor = functionDescriptor,
            rules = rules,
            newCallStack = currentCallStack + expression
          )

          val implementedFunctions = functionDescriptor.getImplementedFunctions(bindingContext)

          implementedFunctions?.forEach { implementedFunctionDescriptor ->
            validateFunctionDescription(
              functionDescriptor = implementedFunctionDescriptor,
              rules = rules,
              newCallStack = currentCallStack + expression
            )
          }
        }
      }
    )
  }

  private fun validateFunctionDescription(
    functionDescriptor: FunctionDescriptor,
    rules: List<RootRule>,
    newCallStack: List<PsiElement>
  ) {
    val newRules =
      rules.filter {
        it.visitFilter.performPassNested(functionDescriptor, bindingContext) is FilterConfig.PassResult.Passed
      }

    if (newRules.isEmpty()) {
      return
    }

    newRules.forEach { methodConfig ->
      val allowedPassResult = methodConfig.allowedInvokes?.performPass(functionDescriptor, bindingContext)

      val (isReport, debugCallStack) =
        if (allowedPassResult != null) {
          if (allowedPassResult is FilterConfig.PassResult.NotPassed) {
            val psiFunctionDescriptor = functionDescriptor.psiElement

            val debugCallStack =
              if (psiFunctionDescriptor != null) {
                newCallStack + psiFunctionDescriptor
              } else {
                newCallStack
              }

            Pair(true, debugCallStack)
          } else {
            Pair(false, listOf())
          }
        } else {
          val notAllowedPassResult = methodConfig.notAllowedInvokes?.performPass(functionDescriptor, bindingContext)

          if (notAllowedPassResult != null) {
            if (notAllowedPassResult is FilterConfig.PassResult.Passed) {
              val debugCallStack =
                if (notAllowedPassResult.onPsiElement != null) {
                  newCallStack + notAllowedPassResult.onPsiElement
                } else {
                  newCallStack
                }

              Pair(true, debugCallStack)
            } else {
              Pair(false, listOf())
            }
          } else {
            Pair(false, listOf())
          }
        }

      if (isReport) {
        report(
          CodeSmell(
            issue,
            Entity.from(debugCallStack.first()),
            message = "${methodConfig.message}.\n\tCall Stack:\n\t\t${
              debugCallStack.drop(1).map {
                Entity.from(
                  it
                )
              }.joinToString(" ---> \n\t\t") { it.compact() }
            }\n",
            references = newCallStack.map { Entity.from(it) }
          )
        )
      }
    }

    val psiElement = functionDescriptor.psiElement

    if (psiElement != null) {
      callChain(
        psiElement = psiElement,
        rules = newRules,
        currentCallStack = newCallStack
      )
    }
  }

  @Serializable
  data class RuleConfig(
    val rootRules: List<RootRule> = emptyList()
  )

  @Serializable
  data class VisitFilterRuleConfig(
    val rootsOnly: FilterExpressionConfig? = null,
    val rootsAndNested: FilterExpressionConfig? = null,
    val nestedOnly: FilterExpressionConfig? = null
  ) {
    fun performPassRoot(
      functionDescriptor: FunctionDescriptor,
      bindingContext: BindingContext
    ): FilterConfig.PassResult =
      tryMergeConfigs(rootsOnly, rootsAndNested)?.performPass(functionDescriptor, bindingContext)
        ?: FilterConfig.PassResult.NotPassed

    fun performPassNested(
      functionDescriptor: FunctionDescriptor,
      bindingContext: BindingContext
    ): FilterConfig.PassResult {
      if (rootsAndNested == null && nestedOnly == null) {
        return FilterConfig.PassResult.Passed(onPsiElement = functionDescriptor.psiElement)
      }

      return tryMergeConfigs(nestedOnly, rootsAndNested)?.performPass(functionDescriptor, bindingContext)
        ?: FilterConfig.PassResult.NotPassed
    }

    fun performPassRoot(
      ktProperty: KtProperty,
      bindingContext: BindingContext
    ): FilterConfig.PassResult =
      tryMergeConfigs(rootsOnly, rootsAndNested)?.performPass(ktProperty, bindingContext)
        ?: FilterConfig.PassResult.NotPassed

    fun performPassNested(
      ktProperty: KtProperty,
      bindingContext: BindingContext
    ): FilterConfig.PassResult {
      if (rootsAndNested == null && nestedOnly == null) {
        return FilterConfig.PassResult.Passed(onPsiElement = ktProperty)
      }

      return tryMergeConfigs(nestedOnly, rootsAndNested)?.performPass(ktProperty, bindingContext)
        ?: FilterConfig.PassResult.NotPassed
    }
  }

  @Serializable
  data class RootRule(
    val message: String,
    val visitFilter: VisitFilterRuleConfig,
    val allowedInvokes: FilterConfig? = null,
    val notAllowedInvokes: FilterConfig? = null
  )
}
