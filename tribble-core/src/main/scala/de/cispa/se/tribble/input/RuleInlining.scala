package de.cispa.se.tribble
package input

/**
  * Inlines rules into their reference sites.
  *
  * <p>E.g.:
  * <blockquote><pre>
  * 'A := "a" ~ 'B ~ 'C,
  * 'B := "b" ~ 'C,
  * 'C := "c"
  * </pre></blockquote>
  * after [[inlineLevels]] = 1 will be transformed into
  * <blockquote><pre>
  * 'A := "a" ~ "b" ~ 'C ~ "c",
  * 'C := "c"
  * </pre></blockquote>
  * and if [[inlineLevels]] is > 1, it will turn into
  * <blockquote><pre>
  * 'A := "a" ~ "b" ~ "c" ~ "c"
  * </pre></blockquote>
  * <p>
  * When a rule is recursive, it will be inlined [[inlineLevels]] times and the original rule will not be removed. E.g.:
  * <blockquote><pre>
  * 'A := "" | "a" ~ 'A
  * </pre></blockquote>
  * will become
  * <blockquote><pre>
  * 'A := "" | "a" ~ ("" | "a" ~ 'A)
  * </pre></blockquote>
  * <p> When there are mutually recursive rules, both will be inlined in each other in a random order. E.g.:
  * <blockquote><pre>
  * 'A := "a" ~ 'B,
  * 'B := "b" ~ 'A
  * </pre></blockquote>
  * Might turn into
  * <blockquote><pre>
  * 'A := "a" ~ "b" ~ "a" ~ 'B,
  * 'B := "b" ~ "a" ~ 'B
  * </pre></blockquote>
  *
  * @param inlineLevels how many times to repeat the inline operation.
  */
class RuleInlining(private val inlineLevels: Int) extends AssemblyPhase {
  require(inlineLevels >= 0, s"The number of inline levels must not be negative! ($inlineLevels given")

  override def process(grammar: GrammarRepr): GrammarRepr = {
    var g = grammar
    for (_ <- 0 until inlineLevels) {
      g = inlineGrammar(g)
    }
    g
  }

  private def inlineGrammar(grammar: GrammarRepr): GrammarRepr = {
    // inline references
    val inlinedRules = grammar.rules.mapValues(inlineRule(_)(grammar)).view.force
    // filter out unused declarations
    grammar.copy(rules = filterUsedReferences(inlinedRules, grammar.start))
  }

  private def inlineRule(rule: DerivationRule)(implicit grammar: GrammarRepr): DerivationRule = rule match {
    case r: Reference => grammar.get(r)
    case Concatenation(elements) => Concatenation(elements.map(inlineRule))
    case Alternation(alts) => Alternation(alts.map(inlineRule))
    case Quantification(subject, min, max) => Quantification(inlineRule(subject), min, max)
    case rule: TerminalRule => rule
  }

  private def filterUsedReferences(rules: Map[NonTerminal, DerivationRule], startSymbol: NonTerminal): Map[NonTerminal, DerivationRule] = {
    var fixpoint = false
    var filteredRules = rules
    do {
      val usedReferences = Set(startSymbol) ++ filteredRules.values.flatMap(_.toStream.flatMap { case r: Reference => Some(r) case _ => None }).map(_.name)
      val newFilteredRules = filteredRules.filterKeys(usedReferences)
      if (newFilteredRules.keySet == filteredRules.keySet)
        fixpoint = true
      filteredRules = newFilteredRules

    } while (!fixpoint)
    filteredRules
  }
}
