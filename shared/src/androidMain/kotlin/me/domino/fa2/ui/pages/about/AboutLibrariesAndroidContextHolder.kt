package me.domino.fa2.ui.pages.about

import android.content.Context

/** Android about-libraries 读取所需的应用 Context 持有器。 */
object AboutLibrariesAndroidContextHolder {
  @Volatile
  var context: Context? = null
    private set

  fun initialize(context: Context) {
    this.context = context.applicationContext
  }
}
