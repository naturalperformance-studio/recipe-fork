package app.recipefork.app.card

/**
 * How the card presents its ingredient and step lists.
 *
 * [VIEW] is compact, for reading a recipe and deciding on it: each line is as tall as its text with a modest gap, a done
 * mark is shown but a tap does not change it, no amount is fixed from a line, and a timer reads as its name and duration
 * (or its remaining time while it runs) with nothing to start it. [COOKING] is for busy hands: lines the card's
 * [CardTarget] tall that a tap marks done, an amount fixed from its line, timers started from their step.
 */
enum class CardMode { VIEW, COOKING }
