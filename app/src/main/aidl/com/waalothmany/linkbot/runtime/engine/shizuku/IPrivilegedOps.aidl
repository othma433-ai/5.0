package com.waalothmany.linkbot.runtime.engine.shizuku;

interface IPrivilegedOps {
    int probeUid() = 1;
    String listUsers() = 2;
    String listPackagesForUser(int userId) = 3;
    int launchPackageForUser(int userId, String packageName) = 4;
    int forceStopPackageForUser(int userId, String packageName) = 5;
    String currentUser() = 6;
    void destroy() = 16777114;
}
