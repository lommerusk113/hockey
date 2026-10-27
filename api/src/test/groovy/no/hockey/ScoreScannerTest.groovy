package no.hockey

import spock.lang.Specification

class ScoreScannerTest extends Specification {

    def players = [
        new Player(1, 'Marcus', 'M', true),
        new Player(2, 'Marius', 'MF', true),
        new Player(3, 'Joakim', 'Jo', true),
        new Player(4, 'Kristian', 'K', true),
        new Player(5, 'Nico', 'Ni', true),
    ]

    def 'resolve #label to #expected'() {
        expect:
            ScoreScanner.resolve(label, players) == expected
        where:
            label    || expected
            'M'      || 1L
            'mf'     || 2L
            ' JO '   || 3L
            'Nico'   || 5L
            'Kri'    || 4L
            'Mar'    || null
            'Si'     || null
            ''       || null
    }

    def 'split merged label #home / #away'() {
        when:
            def match = ScoreScanner.splitMergedLabels(new ScoreScanner.AiMatch(home, away, 2, 3))
        then:
            [match.homeLabel(), match.awayLabel()] == expected
        and:
            [match.homeGoals(), match.awayGoals()] == [2, 3]
        where:
            home      | away || expected
            'K-Mf'    | ''   || ['K', 'Mf']
            'Ni – M'  | ' '  || ['Ni', 'M']
            'K'       | 'Mf' || ['K', 'Mf']
            ''        | ''   || ['', '']
    }

    def 'import CSV and board-style lines'() {
        given:
            def text = '''
                home,away,homeGoals,awayGoals
                K,Mf,4,2
                "Jo";"M";1;3
                Ni	Si	0	0
                # comment
                Si - Ni : 1 - 2
                K-Mf: 3-3
                this is 1 broken line
            '''.stripIndent()
        when:
            def result = ScoreScanner.importText(text, players)
        then:
            result.matches()*.homeLabel() == ['K', 'Jo', 'Ni', 'Si', 'K']
            result.matches()*.awayLabel() == ['Mf', 'M', 'Si', 'Ni', 'Mf']
            result.matches()*.homeGoals() == [4, 1, 0, 1, 3]
            result.matches()*.awayGoals() == [2, 3, 0, 2, 3]
        and:
            result.matches()[0].homePlayerId() == 4L
            result.matches()[0].awayPlayerId() == 2L
            result.matches()[3].homePlayerId() == null
        and:
            result.skipped() == ['this is 1 broken line']
    }
}
