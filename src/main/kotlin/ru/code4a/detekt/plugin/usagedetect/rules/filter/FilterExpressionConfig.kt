package ru.code4a.detekt.plugin.usagedetect.rules.filter

import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.backend.jvm.ir.psiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext

@Serializable
data class FilterExpressionConfig(
  val `is`: FilterConfig? = null,
  val isNot: FilterConfig? = null
)

fun tryMergeConfigs(
  first: FilterExpressionConfig?,
  second: FilterExpressionConfig?
): FilterExpressionConfig? =
  if (first != null && second != null) {
    first.merge(second)
  } else {
    first ?: second
  }

fun FilterExpressionConfig.merge(second: FilterExpressionConfig): FilterExpressionConfig =
  FilterExpressionConfig(
    `is` = tryMergeConfigs(this.`is`, second.`is`),
    isNot = tryMergeConfigs(this.isNot, second.isNot)
  )

private fun Any.tryGetPsiElement(): PsiElement? =
  when (this) {
    is KtProperty -> this
    is FunctionDescriptor -> this.psiElement
    else -> throw IllegalArgumentException("$this is not a KtProperty or FunctionDescriptor")
  }

fun FilterExpressionConfig.tryPerformPass(
  on: Any,
  bindingContext: BindingContext
): FilterConfig.PassResult {
  val isPassResult = `is`?.tryPerformPass(on, bindingContext)
  val isNotPassResult = isNot?.tryPerformPass(on, bindingContext)

  return if (isPassResult != null && isNotPassResult != null) {
    if (isPassResult is FilterConfig.PassResult.Passed && isNotPassResult is FilterConfig.PassResult.NotPassed) {
      isPassResult
    } else {
      FilterConfig.PassResult.NotPassed
    }
  } else if (isPassResult != null) {
    isPassResult
  } else if (isNotPassResult != null) {
    if (isNotPassResult is FilterConfig.PassResult.NotPassed) {
      FilterConfig.PassResult.Passed(
        onPsiElement = on.tryGetPsiElement()
      )
    } else {
      FilterConfig.PassResult.NotPassed
    }
  } else {
    FilterConfig.PassResult.NotPassed
  }
}

fun FilterExpressionConfig.performPass(
  property: KtProperty,
  bindingContext: BindingContext
): FilterConfig.PassResult = tryPerformPass(property, bindingContext)

fun FilterExpressionConfig.performPass(
  functionDescriptor: FunctionDescriptor,
  bindingContext: BindingContext
): FilterConfig.PassResult = tryPerformPass(functionDescriptor, bindingContext)
