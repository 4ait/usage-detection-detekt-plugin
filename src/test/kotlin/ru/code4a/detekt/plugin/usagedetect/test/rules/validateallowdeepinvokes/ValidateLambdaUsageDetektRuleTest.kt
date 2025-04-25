package ru.code4a.detekt.plugin.usagedetect.test.rules.validateallowdeepinvokes

import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.code4a.detekt.plugin.usagedetect.rules.ValidateAllowDeepInvokesDetektRule
import ru.code4a.detekt.plugin.usagedetect.test.extenstions.detekt.lintAllWithContextAndPrint

@KotlinCoreEnvironmentTest
class ValidateLambdaUsageDetektRuleTest(
  private val env: KotlinCoreEnvironment
) {
  @Language("yaml")
  val configYaml =
    """
rootRules:
  - message: Mutation inside lambda is not Allowed for Entity
    visitFilter:
      rootsOnly:
        is:
          lambdaInClassWithAnnotations:
            - test.Entity
      nestedOnly:
        isNot:
          classesWithAnnotations:
            - test.Entity
    notAllowedInvokes:
      classesMutateInvokesWithAnnotations:
        - test.Entity
      creationObjectWithAnnotations:
        - test.Entity
      """.trimIndent()

  @Test
  fun `should not pass with lambda`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                fun setDepartment(department: Department) {
                    val a = {
                      this.department = department
                    }

                  a()
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(1, finding.size)
  }

  @Test
  fun `should not pass with nested`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                fun setDepartment(department: Department) {
                    val a = {
                      val b = {
                        this.department = department
                      }
                    }
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(2, finding.size)
  }

  @Test
  fun `should not pass without this`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                fun setDepartment(department22: Department) {
                    val a = {
                      department = department22
                    }
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(1, finding.size)
  }

  @Test
  fun `should pass with function call`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                fun process(department22: Department) {
                    val a = {
                      setDepartment(department22)
                    }
                }

                fun setDepartment(department22: Department) {
                    this.department = department22
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(0, finding.size)
  }

  @Test
  fun `should not pass with mutation in companion`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                companion object {
                  fun test() {
                    val newObj = Employee()
                    val a = {
                      newObj.department = null
                    }
                  }
                }

                fun setDepartment(department22: Department) {
                    this.department = department22
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(1, finding.size)
  }

  @Test
  fun `should not pass with mutation in companion without this`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                companion object {
                  fun test() {
                    val newObj = Employee().apply {
                      department = null
                    }
                  }
                }

                fun setDepartment(department22: Department) {
                    this.department = department22
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(1, finding.size)
  }

  @Test
  fun `should pass with mutation by function call in companion`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                companion object {
                  fun test() {
                    val newObj = Employee()
                    val a = {
                      newObj.setDepartment(null)
                    }
                  }
                }

                fun setDepartment(department22: Department?) {
                    this.department = department22
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(0, finding.size)
  }

  @Test
  fun `should pass with mutation in companion outside lambda`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                companion object {
                  fun test() {
                    val newObj = Employee()
                    newObj.department = null
                  }
                }

                fun setDepartment(department22: Department) {
                    this.department = department22
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(0, finding.size)
  }

  @Test
  fun `should not pass with creation object in companion inside lambda`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            @Entity
            class Employee {
                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                companion object {
                  fun test() {
                    val a = {
                      val newObj = Employee()
                      newObj
                    }

                    val b = {
                      val c = a()
                      c.department = null
                    }
                  }
                }

                fun setDepartment(department22: Department) {
                    this.department = department22
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(2, finding.size)
  }

  @Test
  fun `should pass without error`() {
    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class Entity
        """.trimIndent(),
        """
package test

            inline fun <T> T?.unwrapElseThrow(exceptionGetter: () -> Throwable): T {
              if (this == null) {
                throw exceptionGetter()
              }

              return this
            }


            @Entity
            class Employee {
                class AA {}

                var id: Long = 0

                var name: String = ""

                var department: Department? = null

                fun getDepartment() = department.unwrapElseThrow {
                    val a = AA()

                    RuntimeException("Object is not initialized")
                }
            }

            @Entity
            class Department {
                var id: Long = 0

                var name: String = ""
            }
        """.trimIndent()
      )

    val finding =
      ValidateAllowDeepInvokesDetektRule(
        TestConfig(
          Pair("configYaml", configYaml),
          Pair("active", "true")
        )
      ).lintAllWithContextAndPrint(env, fileContents)

    Assertions.assertEquals(0, finding.size)
  }
}
