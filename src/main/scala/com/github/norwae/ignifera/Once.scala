package com.github.norwae.ignifera

/**
  * Utility to execute an initialization just once, much like a `lazy` value, but supporting a
  * single parameter.
  * @param build builder function
  * @tparam A parameter type
  * @tparam B result type
  */
class Once[A, B <: AnyRef](build: A ⇒ B) {
  private var value: B = _

  def resolve(param: A): B = synchronized {
    if (null == value) {
      value = build(param)
    }
    value
  }

}
