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
class ValidateAllowDeepInvokesDetektRuleTest(
  private val env: KotlinCoreEnvironment
) {
  @Test
  fun `should found method`() {
    @Language("yaml")
    val configYaml =
      """
rootRules:
  - message: Detected usage NotAllowed without AllowedScope
    visitFilter:
      rootsAndNested:
        isNot:
          methodsWithAnnotations:
            - test.AllowedScope
    notAllowedInvokes:
      methodsWithAnnotations:
        - test.NotAllowed
      """.trimIndent()

    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class NotAllowed

annotation class AllowedScope
        """.trimIndent(),
        """
package test

@NotAllowed
fun a() {
}
        """.trimIndent(),
        """
package test

fun b() {
  a()
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

    assert(finding.size == 1)
  }

  @Test
  fun `should found method inside setter`() {
    @Language("yaml")
    val configYaml =
      """
rootRules:
  - message: Detected usage NotAllowed without AllowedScope
    visitFilter:
      rootsAndNested:
        isNot:
          methodsWithAnnotations:
            - test.AllowedScope
    notAllowedInvokes:
      methodsWithAnnotations:
        - test.NotAllowed
      """.trimIndent()

    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class NotAllowed

annotation class AllowedScope
        """.trimIndent(),
        """
package test

@NotAllowed
fun a() {
}
        """.trimIndent(),
        """
package test

class TestClass {

  var setter: Int = 0
    set(value) {
      a()

      field = value
    }

}
        """.trimIndent(),
        """
package test

fun b(testClass: TestClass) {
  testClass.setter = 2
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

    Assertions.assertTrue(finding.isNotEmpty())
  }

  @Test
  fun `should found method inside setter and class only`() {
    @Language("yaml")
    val configYaml =
      """
rootRules:
  - message: Detected usage NotAllowed without AllowedScope
    visitFilter:
      rootsAndNested:
        isNot:
          methodsWithAnnotations:
            - test.AllowedScope
    notAllowedInvokes:
      methodsWithAnnotations:
        - test.NotAllowed
      """.trimIndent()

    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class NotAllowed

annotation class AllowedScope
        """.trimIndent(),
        """
package test

@NotAllowed
fun a() {
}
        """.trimIndent(),
        """
package test

class TestClass {

  var setter: Int = 0
    set(value) {
      a()

      field = value
    }

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

    Assertions.assertTrue(finding.isNotEmpty())
  }

  @Test
  fun `should found method inside getter and class only`() {
    @Language("yaml")
    val configYaml =
      """
rootRules:
  - message: Detected usage NotAllowed without AllowedScope
    visitFilter:
      rootsAndNested:
        isNot:
          methodsWithAnnotations:
            - test.AllowedScope
    notAllowedInvokes:
      methodsWithAnnotations:
        - test.NotAllowed
      """.trimIndent()

    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class NotAllowed

annotation class AllowedScope
        """.trimIndent(),
        """
package test

@NotAllowed
fun a() {
}
        """.trimIndent(),
        """
package test

class TestClass {

  val setter: Int
    get() {
      a()

      return 0
    }

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

    Assertions.assertTrue(finding.isNotEmpty())
  }

  @Test
  fun `should found method inside delegate`() {
    @Language("yaml")
    val configYaml =
      """
rootRules:
  - message: Detected usage NotAllowed without AllowedScope
    visitFilter:
      rootsOnly:
        isNot:
          packages:
            - ttt
    notAllowedInvokes:
      methodsWithAnnotations:
        - test.NotAllowed
      """.trimIndent()

    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class NotAllowed

annotation class AllowedScope
        """.trimIndent(),
        """
package test

@NotAllowed
fun a() {
}
        """.trimIndent(),
        """
package test

class TestClass {

  val setter by lazy {
    a()

    1
  }

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

    Assertions.assertTrue(finding.isNotEmpty())
  }

  @Test
  fun `should not found method inside delegate`() {
    @Language("yaml")
    val configYaml =
      """
rootRules:
  - message: Detected usage NotAllowed without AllowedScope
    visitFilter:
      rootsOnly:
        isNot:
          packages:
            - transaprent
    notAllowedInvokes:
      methodsWithAnnotations:
        - test.NotAllowed
      """.trimIndent()

    @Language("kotlin")
    val fileContents =
      listOf(
        """
package test

annotation class NotAllowed

annotation class AllowedScope
        """.trimIndent(),
        """
package test

@NotAllowed
fun a() {
}
        """.trimIndent(),
        """
package transaprent
import test.a

class TestClass {

  val setter by lazy {
    a()

    1
  }

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

    Assertions.assertTrue(finding.isEmpty())
  }
}
