package com.meticulouscreations.homesafe

/**
 * [context] is the application's, and so is the same for as long as the process is. What needs a
 * window ([ActivityAccess]: permission dialogs, biometric prompts) is asked for through
 * [activities], which is null for a graph with no screen (a receiver, a worker, a push): the parts
 * that need one then do without.
 */
actual class PlatformContext(val context: android.content.Context, val activities: ActivityAccess? = null)
