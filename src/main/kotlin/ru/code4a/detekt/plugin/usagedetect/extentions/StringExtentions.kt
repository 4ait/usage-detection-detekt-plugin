package ru.code4a.detekt.plugin.usagedetect.extentions

fun String.isPartOfPackageName(packageName: String): Boolean =
  packageName == this ||
    packageName.startsWith("$this.")
