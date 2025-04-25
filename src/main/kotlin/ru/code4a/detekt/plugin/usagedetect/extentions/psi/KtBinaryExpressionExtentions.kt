package ru.code4a.detekt.plugin.usagedetect.extentions.psi

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

fun KtBinaryExpression.isMutationOfThis(bindingContext: BindingContext): Boolean {
  val left = left

  if (left is KtDotQualifiedExpression) {
    val isSkip =
      (left.selectorExpression as? KtNameReferenceExpression)
        ?.hasAnyAnnotation(listOf("jakarta.persistence.Transient"), bindingContext) ?: false // TODO(configuration)

    if (isSkip) {
      return false
    }

    if (left.receiverExpression is KtThisExpression || left.isClassMember(bindingContext)) {
      return true
    }
  }

  return false
}

fun KtBinaryExpression.isLeftOfClassType(bindingContext: BindingContext, classDescriptor: ClassDescriptor): Boolean {
  val left = left ?: return false

  if (left is KtThisExpression) {
    val thisType = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, left]?.type
    val thisClassDescriptor = thisType?.constructor?.declarationDescriptor as? ClassDescriptor
    return thisClassDescriptor == classDescriptor
  }

  if (left is KtDotQualifiedExpression) {
    val receiver = left.receiverExpression

    if (receiver is KtThisExpression) {
      val thisType = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, receiver]?.type
      val thisClassDescriptor = thisType?.constructor?.declarationDescriptor as? ClassDescriptor
      return thisClassDescriptor == classDescriptor
    }

    if (left.selectorExpression?.isClassMember(bindingContext) == true) {
      val resolvedCall = left.selectorExpression.getResolvedCall(bindingContext)
      val containingClass = resolvedCall?.candidateDescriptor?.containingDeclaration as? ClassDescriptor
      return containingClass == classDescriptor
    }
  }

  if (left is KtNameReferenceExpression) {
    val resolvedCall = left.getResolvedCall(bindingContext)
    val containingClass = resolvedCall?.candidateDescriptor?.containingDeclaration as? ClassDescriptor
    return containingClass == classDescriptor
  }

  return false
}

fun KtBinaryExpression.isMutationOfClass(bindingContext: BindingContext, classDescriptor: ClassDescriptor): Boolean {
  return operationToken in mutableListOf(
    KtTokens.EQ,
    KtTokens.PLUSEQ,
    KtTokens.MINUSEQ,
    KtTokens.MULTEQ,
    KtTokens.DIVEQ
  ) &&
    isLeftOfClassType(bindingContext, classDescriptor)
}
