package ru.code4a.detekt.plugin.usagedetect

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import ru.code4a.detekt.plugin.usagedetect.rules.ValidateAllowDeepInvokesDetektRule
import ru.code4a.detekt.plugin.usagedetect.rules.ValidateAllowUsagesFunctionsDetektRule

class DetektRuleSetProvider : RuleSetProvider {
  override val ruleSetId: String = "foura_usage_detection"

  override fun instance(config: Config): RuleSet =
    RuleSet(
      ruleSetId,
      listOf(
        ValidateAllowDeepInvokesDetektRule(config),
        ValidateAllowUsagesFunctionsDetektRule(config)
      )
    )
}
