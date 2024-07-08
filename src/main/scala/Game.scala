case class Game(playerA: Player, playerB: Player) {

  private val winner: Option[Player] =
    (playerA, playerB) match {
      case (a@Player(sa), b@Player(sb)) =>
        if (sa.wins(sb)) Some(a)
        else Some(b)
      case _ => None
    }

  val result = winner.map(player => s"$player wins")
    .getOrElse("it's a draw")

}

object Game {


  def apply(text: String): Game =
    text.split("-", 2) match {
      case Array(playerA, playerB) =>
        apply(Player(playerA), Player(playerB))
      case _ =>
        val errorMsg = s"Invalid game $text. " +
          s"Please, use the format <name>: <symbol> - <name>: <symbol>"
        throw new IllegalArgumentException(errorMsg)
    }

}
