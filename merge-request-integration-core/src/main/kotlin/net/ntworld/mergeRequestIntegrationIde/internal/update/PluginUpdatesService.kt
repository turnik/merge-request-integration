package net.ntworld.mergeRequestIntegrationIde.internal.update

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.concurrency.AppExecutorUtil
import net.ntworld.mergeRequestIntegrationIde.task.GetAvailableUpdatesTask
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PluginUpdatesService : Disposable {

    companion object {
        private const val CHECK_INTERVAL = 3600000L // Every 1 hour
    }

    private lateinit var scheduledCheck: ScheduledFuture<*>
    private val notificationShowed: AtomicBoolean = AtomicBoolean(false)

    init {
        val application = ApplicationManager.getApplication()

        if (!application.isCommandLine) {
            application.messageBus.connect()
                    .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                        override fun projectOpened(project: Project) {
                            StartupManager.getInstance(project)
                                    .registerPostStartupActivity { run(project) }
                        }
                    })
        }

        /* Register hook for low memory situation, in current impl we cancel checks until next startup */
        LowMemoryWatcher.register(Runnable { cancelScheduledCheck() }, this)
    }

    private fun run(project: Project) {
        runCheck(project).doWhenProcessed { scheduleNextCheck(project) }
    }

    private fun runCheck(project: Project): ActionCallback {
        val callback = ActionCallback()

        GetAvailableUpdatesTask(project, object : GetAvailableUpdatesTask.Listener {
            override fun dataReceived(updates: List<String>) {
                updates.takeIf { it.isNotEmpty() }
                        ?.let {

                        }
            }

            override fun onError(exception: Exception) {
                callback.reject(exception.message)
            }
        }).start()

        return callback
    }

    private fun scheduleNextCheck(project: Project) {
        scheduledCheck = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule({ run(project) }, CHECK_INTERVAL, TimeUnit.MILLISECONDS)
    }

    private fun cancelScheduledCheck() {
        synchronized(this) {
            scheduledCheck.takeIf { !it.isCancelled }?.let {
                it.cancel(false)
            }
        }
    }

    override fun dispose() {
        cancelScheduledCheck()
    }
}