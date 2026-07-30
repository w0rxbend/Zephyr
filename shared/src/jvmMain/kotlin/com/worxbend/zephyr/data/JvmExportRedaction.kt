package com.worxbend.zephyr.data

internal fun defaultSensitiveExportPaths(
    userHome: String? = System.getProperty("user.home"),
    environmentSdkmanHome: String? = System.getenv("SDKMAN_DIR"),
    configuredSdkmanHome: String? = JvmSdkmanHomeConfigurationService().resolveHome().toString(),
): List<String> =
    listOfNotNull(userHome, environmentSdkmanHome, configuredSdkmanHome)
        .filter(String::isNotBlank)
        .distinct()
