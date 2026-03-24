package me.domino.fa2.ui.navigation

import cafe.adriel.voyager.navigator.Navigator

/** 返回最外层导航器。 */
fun Navigator.rootNavigator(): Navigator {
  var cursor: Navigator = this
  while (cursor.parent != null) {
    cursor = cursor.parent!!
  }
  return cursor
}

/** 回到 Home（保留根页面）。 */
fun Navigator.goBackHome() {
  rootNavigator().popUntilRoot()
}
