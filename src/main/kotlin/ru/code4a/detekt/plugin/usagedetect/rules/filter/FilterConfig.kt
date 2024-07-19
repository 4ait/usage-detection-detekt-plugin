package ru.code4a.detekt.plugin.usagedetect.rules.filter

import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.backend.jvm.ir.psiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.containingPackage
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import ru.code4a.detekt.plugin.usagedetect.extentions.isPartOfPackageName
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.determineClassSelfMutateByPsiElement
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.getAnnotationsOutsideCompanion
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.getClassNameOutsideCompanion
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.getContainingClassDescriptorOutsideCompanion
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.getContainingFunctionDescriptor
import ru.code4a.detekt.plugin.usagedetect.extentions.psi.isTopLevelFunction

@Serializable
data class FilterConfig(
  val classes: List<String> = emptyList(),
  val classesMethods: List<ClassMethodConfig> = emptyList(),
  val packages: List<String> = emptyList(),
  val methodsWithAnnotations: List<String> = emptyList(),
  val methodsWithParametrizedAnnotations: List<MethodWithParamAnnotationConfig> = emptyList(),
  val classesWithAnnotations: List<String> = emptyList(),
  val classesMutateInvokesWithAnnotations: List<String> = emptyList(),
  val topLevelFunction: Boolean? = null,
  val classObjectFunction: Boolean? = null,
  val classWithoutAnnotations: Boolean? = null
) {
  sealed interface PassResult {
    class Passed(
      val onPsiElement: PsiElement?
    ) : PassResult

    data object NotPassed : PassResult
  }

  @Serializable
  data class ClassMethodConfig(
    val name: String,
    val methods: List<String>
  )

  @Serializable
  data class MethodWithParamAnnotationConfig(
    val annotation: String,
    val parameters: List<AnnotationParameter>
  ) {
    @Serializable
    data class AnnotationParameter(
      val name: String,
      val value: String
    )
  }
}

fun FilterConfig.merge(second: FilterConfig): FilterConfig =
  FilterConfig(
    classes = this.classes + second.classes,
    classesMethods = this.classesMethods + second.classesMethods,
    packages = this.packages + second.packages,
    methodsWithAnnotations = this.methodsWithAnnotations + second.methodsWithAnnotations,
    classesWithAnnotations = this.classesWithAnnotations + second.classesWithAnnotations,
    classesMutateInvokesWithAnnotations = this.classesMutateInvokesWithAnnotations + second.classesMutateInvokesWithAnnotations,
    topLevelFunction = this.topLevelFunction == true || second.topLevelFunction == true,
    classObjectFunction = this.classObjectFunction == true || second.classObjectFunction == true,
    classWithoutAnnotations = this.classWithoutAnnotations == true || second.classWithoutAnnotations == true
  )

fun tryMergeConfigs(
  first: FilterConfig?,
  second: FilterConfig?
): FilterConfig? =
  if (first != null && second != null) {
    first.merge(second)
  } else {
    first ?: second
  }

fun FilterConfig.tryPerformPass(
  on: Any,
  bindingContext: BindingContext
): FilterConfig.PassResult {
  when (on) {
    is FunctionDescriptor -> {
      val functionDescriptor = on

      if (packages.isNotEmpty()) {
        val containingPackageName = functionDescriptor.containingPackage()?.asString()

        if (containingPackageName != null) {
          if (packages.firstOrNull { allowedPackageName -> allowedPackageName.isPartOfPackageName(containingPackageName) } != null) {
            return FilterConfig.PassResult.Passed(
              onPsiElement = functionDescriptor.psiElement
            )
          }
        }
      }

      if (classes.isNotEmpty()) {
        val currentClassName = functionDescriptor.containingDeclaration.getClassNameOutsideCompanion()

        if (classes.firstOrNull { allowedClassName -> allowedClassName == currentClassName } != null) {
          return FilterConfig.PassResult.Passed(
            onPsiElement = functionDescriptor.psiElement
          )
        }
      }

      if (classesMethods.isNotEmpty()) {
        val currentClassName = functionDescriptor.containingDeclaration.getClassNameOutsideCompanion()

        if (
          classesMethods.any { classMethodConfig ->
            classMethodConfig.name == currentClassName &&
              classMethodConfig.methods.any { methodName -> methodName == functionDescriptor.name.asString() }
          }
        ) {
          return FilterConfig.PassResult.Passed(
            onPsiElement = functionDescriptor.psiElement
          )
        }
      }

      if (methodsWithAnnotations.isNotEmpty()) {
        for (containingFunctionAnnotation in functionDescriptor.annotations) {
          for (allowedMethodWithAnnotationConfig in methodsWithAnnotations) {
            if (containingFunctionAnnotation.fqName?.asString() == allowedMethodWithAnnotationConfig) {
              return FilterConfig.PassResult.Passed(
                onPsiElement = functionDescriptor.psiElement
              )
            }
          }
        }
      }

      if (methodsWithParametrizedAnnotations.isNotEmpty()) {
        for (containingFunctionAnnotation in functionDescriptor.annotations) {
          for (allowedMethodWithParametrizedAnnotationConfig in methodsWithParametrizedAnnotations) {
            if (containingFunctionAnnotation.fqName?.asString() == allowedMethodWithParametrizedAnnotationConfig.annotation) {
              val matchedCount =
                allowedMethodWithParametrizedAnnotationConfig.parameters.count { annotationParameter ->
                  containingFunctionAnnotation
                    .allValueArguments[Name.identifier(annotationParameter.name)]
                    ?.value
                    ?.toString() == annotationParameter.value
                }

              if (matchedCount == allowedMethodWithParametrizedAnnotationConfig.parameters.size) {
                return FilterConfig.PassResult.Passed(
                  onPsiElement = functionDescriptor.psiElement
                )
              }
            }
          }
        }
      }

      if (classesWithAnnotations.isNotEmpty()) {
        val containingDescriptor = functionDescriptor.containingDeclaration
        if (containingDescriptor is ClassDescriptor) {
          val classAnnotations = containingDescriptor.getAnnotationsOutsideCompanion()

          if (classAnnotations != null) {
            for (containingClassAnnotation in classAnnotations) {
              for (allowedClassWithAnnotationConfig in classesWithAnnotations) {
                if (containingClassAnnotation.fqName?.asString() == allowedClassWithAnnotationConfig) {
                  return FilterConfig.PassResult.Passed(
                    onPsiElement = functionDescriptor.psiElement
                  )
                }
              }
            }
          }
        }
      }

      if (classesMutateInvokesWithAnnotations.isNotEmpty()) {
        val functionDescriptorPsiElement = functionDescriptor.psiElement

        if (functionDescriptorPsiElement != null) {
          val containingDescriptor = functionDescriptor.containingDeclaration
          if (containingDescriptor is ClassDescriptor) {
            val classAnnotations = containingDescriptor.getAnnotationsOutsideCompanion()

            if (classAnnotations != null) {
              for (containingClassAnnotation in classAnnotations) {
                for (allowedClassWithAnnotationConfig in classesMutateInvokesWithAnnotations) {
                  if (containingClassAnnotation.fqName?.asString() == allowedClassWithAnnotationConfig) {
                    val mutatePsiElement = functionDescriptorPsiElement.determineClassSelfMutateByPsiElement(bindingContext)

                    if (mutatePsiElement != null) {
                      return FilterConfig.PassResult.Passed(
                        onPsiElement = mutatePsiElement
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      if (topLevelFunction == true) {
        if (functionDescriptor.isTopLevelFunction()) {
          return FilterConfig.PassResult.Passed(
            onPsiElement = functionDescriptor.psiElement
          )
        }
      }

      if (classObjectFunction == true) {
        val containingDescriptor = functionDescriptor.containingDeclaration
        if (containingDescriptor is ClassDescriptor && containingDescriptor.kind == ClassKind.OBJECT) {
          return FilterConfig.PassResult.Passed(
            onPsiElement = functionDescriptor.psiElement
          )
        }
      }

      if (classWithoutAnnotations == true) {
        val containingDescriptor = functionDescriptor.containingDeclaration
        if (containingDescriptor is ClassDescriptor && containingDescriptor.kind == ClassKind.CLASS) {
          if (containingDescriptor.annotations.isEmpty()) {
            return FilterConfig.PassResult.Passed(
              onPsiElement = functionDescriptor.psiElement
            )
          }
        }
      }

      return FilterConfig.PassResult.NotPassed
    }
    is KtProperty -> {
      val property = on

      val expression =
        if (property.delegate?.expression != null) {
          property.delegate?.expression
        } else if (property.getter?.bodyExpression != null) {
          property.getter?.bodyExpression
        } else if (property.setter?.bodyExpression != null) {
          property.setter?.bodyExpression
        } else {
          null
        }

      if (packages.isNotEmpty()) {
        val containingPackageName = property.containingKtFile.packageFqName.asString()

        if (packages.firstOrNull { allowedPackageName -> allowedPackageName.isPartOfPackageName(containingPackageName) } != null) {
          return FilterConfig.PassResult.Passed(
            onPsiElement = property
          )
        }
      }

      if (classesWithAnnotations.isNotEmpty()) {
        val containingClass = property.getContainingClassDescriptorOutsideCompanion(bindingContext)
        val classAnnotations = containingClass?.annotations

        if (classAnnotations != null) {
          for (containingClassAnnotation in classAnnotations) {
            for (allowedClassWithAnnotationConfig in classesWithAnnotations) {
              if (containingClassAnnotation.fqName?.asString() == allowedClassWithAnnotationConfig) {
                return FilterConfig.PassResult.Passed(
                  onPsiElement = expression
                )
              }
            }
          }
        }
      }

      return FilterConfig.PassResult.NotPassed
    }
    is KtCallExpression -> {
      val expression = on

      if (packages.isNotEmpty()) {
        val ktFile = expression.getParentOfType<KtFile>(true)
        val containingPackageName = ktFile?.packageFqName?.asString()

        if (containingPackageName != null &&
          packages.firstOrNull { allowedPackageName -> containingPackageName.startsWith(allowedPackageName) } != null
        ) {
          return FilterConfig.PassResult.Passed(
            onPsiElement = expression
          )
        }
      }

      if (classes.isNotEmpty()) {
        val containingClass = expression.getContainingClassDescriptorOutsideCompanion(bindingContext)

        if (containingClass != null) {
          if (classes.firstOrNull { allowedClassName -> allowedClassName == containingClass.fqNameSafe.asString() } != null) {
            return FilterConfig.PassResult.Passed(
              onPsiElement = expression
            )
          }
        }
      }

      if (classesMethods.isNotEmpty()) {
        val containingClass = expression.getContainingClassDescriptorOutsideCompanion(bindingContext)
        val containingFunction = expression.getContainingFunctionDescriptor(bindingContext)

        if (
          classesMethods.any { classMethodConfig ->
            classMethodConfig.name == containingClass?.fqNameSafe?.asString() &&
              classMethodConfig.methods.any { methodName -> methodName == containingFunction?.name?.asString() }
          }
        ) {
          return FilterConfig.PassResult.Passed(
            onPsiElement = expression
          )
        }
      }

      if (methodsWithAnnotations.isNotEmpty()) {
        val containingFunction = expression.getContainingFunctionDescriptor(bindingContext)

        if (containingFunction?.annotations != null) {
          for (containingFunctionAnnotation in containingFunction.annotations) {
            for (allowedMethodWithAnnotationConfig in methodsWithAnnotations) {
              if (containingFunctionAnnotation.fqName?.asString() == allowedMethodWithAnnotationConfig) {
                return FilterConfig.PassResult.Passed(
                  onPsiElement = expression
                )
              }
            }
          }
        }
      }

      if (classesWithAnnotations.isNotEmpty()) {
        val containingClass = expression.getContainingClassDescriptorOutsideCompanion(bindingContext)
        val classAnnotations = containingClass?.annotations

        if (classAnnotations != null) {
          for (containingClassAnnotation in classAnnotations) {
            for (allowedClassWithAnnotationConfig in classesWithAnnotations) {
              if (containingClassAnnotation.fqName?.asString() == allowedClassWithAnnotationConfig) {
                return FilterConfig.PassResult.Passed(
                  onPsiElement = expression
                )
              }
            }
          }
        }
      }

      return FilterConfig.PassResult.NotPassed
    }
  }

  throw IllegalStateException("On type is not supported: ${on::class}")
}

fun FilterConfig.performPass(
  functionDescriptor: FunctionDescriptor,
  bindingContext: BindingContext
): FilterConfig.PassResult = tryPerformPass(functionDescriptor, bindingContext)

fun FilterConfig.performPass(
  property: KtProperty,
  bindingContext: BindingContext
): FilterConfig.PassResult = tryPerformPass(property, bindingContext)

fun FilterConfig.performPass(
  expression: KtCallExpression,
  bindingContext: BindingContext
): FilterConfig.PassResult = tryPerformPass(expression, bindingContext)
