package com.saman.tunnel

object NativeBridge {
    init {
        System.loadLibrary("aether")
        System.loadLibrary("samanbridge")
    }

    external fun version(): String
    external fun startCore(argumentsJson: String, workDir: String): String
    external fun pollJob(jobId: Long): String
    external fun cancelJob(jobId: Long): String
    external fun freeJob(jobId: Long): String
}
