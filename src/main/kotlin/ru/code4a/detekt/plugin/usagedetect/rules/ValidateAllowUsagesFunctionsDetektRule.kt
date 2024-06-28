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
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.constants.AnnotationValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.constants.TypedArrayValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.getOriginalAnnotations
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.getOriginalClassName
import ru.code4a.detekt.plugin.usagedetect.rules.filter.FilterConfig
import ru.code4a.detekt.plugin.usagedetect.rules.filter.performPass

@RequiresTypeResolution
class ValidateAllowUsagesFunctionsDetektRule(
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

  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)

    validateWithConfig(expression)
    validateWithAnnotations(expression)
  }

  private fun validateWithAnnotations(expression: KtCallExpression) {
    val resolvedCall = expression.getResolvedCall(bindingContext)

    if (resolvedCall != null) {
      val resultingDescriptor = resolvedCall.resultingDescriptor
      val functionAnnotations = resultingDescriptor.annotations
      val containingDeclaration = resultingDescriptor.containingDeclaration

      val annotations =
        if (containingDeclaration is ClassDescriptor) {
          functionAnnotations + containingDeclaration.annotations
        } else {
          functionAnnotations
        }

      val filterClasses = mutableListOf<String>()
      val filterPackages = mutableListOf<String>()
      val filterClassesMethods = mutableListOf<FilterConfig.ClassMethodConfig>()

      annotations.forEach { annotation ->
        val annotationName = annotation.fqName?.asString()

        if (annotationName == ruleConfig.allowedUsageOnClassAnnotation) {
          val clazzConstantValue = annotation.allValueArguments[Name.identifier("clazz")]!! as KClassValue

          val allowedUsageInClass = clazzConstantValue.value as KClassValue.Value.NormalClass

          val allowedUsageInClassName = allowedUsageInClass.classId.asString().replace("/", ".")

          filterClasses.add(allowedUsageInClassName)
        } else if (annotationName == ruleConfig.allowedUsageOnClassesAnnotation) {
          val classesConstantValue = annotation.allValueArguments[Name.identifier("classes")]!! as TypedArrayValue

          val allowedClasses =
            (classesConstantValue.value as ArrayList<KClassValue>)
              .map {
                (it.value as KClassValue.Value.NormalClass).classId.asString().replace("/", ".")
              }

          filterClasses.addAll(allowedClasses)
        } else if (annotationName == ruleConfig.allowedUsageOnlyInPackageAnnotation) {
          val packageNameConstantValue =
            annotation.allValueArguments[Name.identifier("packageName")]!! as StringValue

          val allowedPackageName = packageNameConstantValue.value

          filterPackages.add(allowedPackageName)
        } else if (annotationName == ruleConfig.allowedUsageOnlyInMethodAnnotation) {
          val clazzConstantValue = annotation.allValueArguments[Name.identifier("clazz")]!! as KClassValue
          val methodConstantValue = annotation.allValueArguments[Name.identifier("method")]!! as StringValue

          val allowedUsageInClass = clazzConstantValue.value as KClassValue.Value.NormalClass

          val allowedUsageInClassName = allowedUsageInClass.classId.asString().replace("/", ".")

          filterClassesMethods.add(
            FilterConfig.ClassMethodConfig(
              name = allowedUsageInClassName,
              methods = listOf(methodConstantValue.value)
            )
          )
        } else if (annotationName == ruleConfig.allowedUsageOnlyInMethodsAnnotation) {
          val methodsAnnotationsConstants = annotation.allValueArguments[Name.identifier("method")]!! as TypedArrayValue

          val classesMethods =
            (methodsAnnotationsConstants.value as ArrayList<AnnotationValue>).map { annotationValue ->
              val innerAnnotationDescriptor = annotationValue.value

              val clazzConstantValue = innerAnnotationDescriptor.allValueArguments[Name.identifier("clazz")]!! as KClassValue
              val methodConstantValue = innerAnnotationDescriptor.allValueArguments[Name.identifier("method")]!! as StringValue

              val allowedUsageInClass = clazzConstantValue.value as KClassValue.Value.NormalClass

              val allowedUsageInClassName = allowedUsageInClass.classId.asString().replace("/", ".")

              FilterConfig.ClassMethodConfig(
                name = allowedUsageInClassName,
                methods = listOf(methodConstantValue.value)
              )
            }

          filterClassesMethods.addAll(classesMethods)
        }
      }

      if (filterClasses.isNotEmpty() || filterPackages.isNotEmpty() || filterClassesMethods.isNotEmpty()) {
        validateExpressionUsages(
          expression = expression,
          allowedIn =
            FilterConfig(
              classes = filterClasses,
              packages = filterPackages,
              classesMethods = filterClassesMethods
            )
        )
      }
    }
  }

  private fun expressionIsInvoke(
    expression: KtCallExpression,
    invokeConfig: InvokeConfig
  ): Boolean {
    val calleeExpression = expression.calleeExpression
    val methodName = calleeExpression?.text

    val resolvedCall = expression.getResolvedCall(bindingContext)
    val dispatchReceiver = resolvedCall?.dispatchReceiver
    val receiverType = dispatchReceiver?.type
    val declarationDescriptor = receiverType?.constructor?.declarationDescriptor

    val className =
      declarationDescriptor?.getOriginalClassName()
        ?: resolvedCall
          ?.resultingDescriptor
          ?.containingDeclaration
          ?.fqNameSafe
          ?.asString()

    for (targetClassRuleConfig in invokeConfig.classes) {
      if (targetClassRuleConfig.methods.isEmpty()) {
        if (className == targetClassRuleConfig.name) {
          return true
        }
      } else {
        for (targetMethodName in targetClassRuleConfig.methods) {
          if (className == targetClassRuleConfig.name && methodName == targetMethodName) {
            return true
          }
        }
      }
    }

    val originalAnnotations = declarationDescriptor?.getOriginalAnnotations()

    if (originalAnnotations != null) {
      for (annotatedClassConfig in invokeConfig.annotatedClasses) {
        for (classAnnotation in originalAnnotations) {
          for (annotationConfig in annotatedClassConfig.annotations) {
            val classAnnotationName = classAnnotation.fqName?.asString()

            if (classAnnotationName == annotationConfig) {
              return true
            }
          }
        }
      }
    }

    val functionDescriptor = resolvedCall?.resultingDescriptor as? FunctionDescriptor

    if (functionDescriptor != null) {
      for (methodAnnotationNameConfig in invokeConfig.methodsWithAnnotations) {
        for (functionAnnotation in functionDescriptor.annotations) {
          val functionAnnotationName = functionAnnotation.fqName?.asString()

          if (functionAnnotationName == methodAnnotationNameConfig) {
            return true
          }
        }
      }
    }

    return false
  }

  private fun validateWithConfig(expression: KtCallExpression) {
    for (invokeConfig in ruleConfig.invokes) {
      if (expressionIsInvoke(expression, invokeConfig)) {
        validateExpressionUsages(
          expression,
          allowedIn = invokeConfig.allowedIn,
          notAllowedIn = invokeConfig.notAllowedIn
        )
      }
    }
  }

  private fun validateExpressionUsages(
    expression: KtCallExpression,
    allowedIn: FilterConfig? = null,
    notAllowedIn: FilterConfig? = null
  ) {
    if (
      (allowedIn != null && allowedIn.performPass(expression, bindingContext) is FilterConfig.PassResult.NotPassed) ||
      (notAllowedIn != null && notAllowedIn.performPass(expression, bindingContext) is FilterConfig.PassResult.Passed)
    ) {
      val calleeExpression = expression.calleeExpression
      val methodName = calleeExpression?.text
      val resolvedCall = expression.getResolvedCall(bindingContext)
      val dispatchReceiver = resolvedCall?.dispatchReceiver
      val receiverType = dispatchReceiver?.type

      val callOnClass =
        receiverType
          ?.constructor
          ?.declarationDescriptor
          ?.fqNameSafe
          ?.asString()

      report(
        CodeSmell(
          issue,
          Entity.from(expression),
          message = "Call method $methodName of $callOnClass is not allowed here"
        )
      )
    }
  }

  @Serializable
  data class RuleConfig(
    val invokes: List<InvokeConfig> = emptyList(),
    val allowedUsageOnClassAnnotation: String = "",
    val allowedUsageOnClassesAnnotation: String = "",
    val allowedUsageOnlyInPackageAnnotation: String = "",
    val allowedUsageOnlyInMethodAnnotation: String = "",
    val allowedUsageOnlyInMethodsAnnotation: String = ""
  )

  @Serializable
  data class InvokeConfig(
    val classes: List<TargetClassRuleConfig> = emptyList(),
    val annotatedClasses: List<AnnotatedClassConfig> = emptyList(),
    val methodsWithAnnotations: List<String> = emptyList(),
    val allowedIn: FilterConfig? = null,
    val notAllowedIn: FilterConfig? = null
  )

  @Serializable
  data class TargetClassRuleConfig(
    val name: String,
    val methods: List<String> = emptyList()
  )

  @Serializable
  data class AnnotatedClassConfig(
    val annotations: List<String>
  )
}
