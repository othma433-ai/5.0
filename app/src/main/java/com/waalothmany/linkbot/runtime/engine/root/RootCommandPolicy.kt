package com.waalothmany.linkbot.runtime.engine.root

sealed interface RootCommand {
    data object ProbeUid : RootCommand
    data object ListUsers : RootCommand
    data object CurrentUser : RootCommand
    data class ListPackagesForUser(val userId: Int) : RootCommand
    data class ResolvePackageForUser(val userId: Int, val packageName: String) : RootCommand
    data class StartComponentForUser(val userId: Int, val componentName: String) : RootCommand
    data class ForceStopPackageForUser(val userId: Int, val packageName: String) : RootCommand
}

object RootCommandPolicy {
    private val packagePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
    private val componentPattern = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

    fun render(command: RootCommand): String = when (command) {
        RootCommand.ProbeUid -> "id -u"
        RootCommand.ListUsers -> "pm list users"
        RootCommand.CurrentUser -> "am get-current-user"
        is RootCommand.ListPackagesForUser ->
            "pm list packages --user ${validUserId(command.userId)}"
        is RootCommand.ResolvePackageForUser ->
            "cmd package resolve-activity --brief --user ${validUserId(command.userId)} ${validPackage(command.packageName)}"
        is RootCommand.StartComponentForUser ->
            "am start --user ${validUserId(command.userId)} -n ${validComponent(command.componentName)}"
        is RootCommand.ForceStopPackageForUser ->
            "am force-stop --user ${validUserId(command.userId)} ${validPackage(command.packageName)}"
    }

    fun isValidComponent(value: String): Boolean = componentPattern.matches(value)

    private fun validUserId(value: Int): Int {
        require(value in 0..99_999) { "Invalid Android user id" }
        return value
    }

    private fun validPackage(value: String): String {
        require(packagePattern.matches(value)) { "Invalid package name" }
        return value
    }

    private fun validComponent(value: String): String {
        require(componentPattern.matches(value)) { "Invalid component name" }
        return value
    }
}
