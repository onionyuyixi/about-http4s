package others

sealed trait Symbol {

  protected def beats: List[Symbol]

  def wins(symbol: Symbol): Boolean =
    beats.contains(symbol)
}

case object Rock extends Symbol {

  override protected def beats: List[Symbol] = List()

}

case object Paper extends Symbol {
  protected val beats = List(Rock, Spock)
}

case object Scissors extends Symbol {
  protected val beats = List(Paper, Lizard)
}

case object Lizard extends Symbol {
  protected val beats = List(Spock, Paper)
}

case object Spock extends Symbol {
  protected val beats = List(Scissors, Rock)
}

object Symbol {

  def fromString(s: String): Symbol =
    s.trim.toLowerCase match {
      case "rock" => Rock
      case "paper" => Paper
      case "scissors" => Scissors
      case "lizard" => Lizard
      case "spock" => Spock
      case unknown =>
        throw new IllegalArgumentException("symbol parse error")
    }




}

