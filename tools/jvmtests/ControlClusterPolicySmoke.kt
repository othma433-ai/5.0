package com.waalothmany.linkbot.automation

fun main() {
    check(ControlClusterPolicy.accept(
        labelMatched = true,
        strictStructuralControl = true,
        clickablePath = false,
        peerLabelsFound = 0,
    ))
    check(ControlClusterPolicy.accept(
        labelMatched = true,
        strictStructuralControl = false,
        clickablePath = true,
        peerLabelsFound = 2,
    ))
    check(!ControlClusterPolicy.accept(
        labelMatched = true,
        strictStructuralControl = false,
        clickablePath = true,
        peerLabelsFound = 1,
    ))
    check(!ControlClusterPolicy.accept(
        labelMatched = false,
        strictStructuralControl = true,
        clickablePath = true,
        peerLabelsFound = 3,
    ))
    println("ControlClusterPolicySmoke: PASS")
}
