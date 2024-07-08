case class Player(name: String, symbol: Symbol) {
  override def toString = s" Player ${name} with symbol ${symbol}"
}

object Player {

  def apply(text: String): Player =
    text.split(":", 2) match {
      case Array(name, symbol) =>
        new Player(name, Symbol.fromString(symbol))
      case _ => throw new IllegalArgumentException("player input error")
    }

  def unapply(player: Player): Option[Symbol] = Some(player.symbol)

}


