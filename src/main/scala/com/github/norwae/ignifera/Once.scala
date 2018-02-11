package com.github.norwae.ignifera

class Once[A, B](build: A ⇒ B) {
  private var value: B = _

  def resolve(param: A): B = synchronized {
    if (null == value) {
      value = build(param)
    }
    value
  }

}
