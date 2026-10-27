package no.hockey

import spock.lang.Specification

class ScoreScannerTest extends Specification {

    def players = [
        new Player(1, 'Marcus', 'M'),
        new Player(2, 'Marius', 'MF'),
        new Player(3, 'Joakim', 'Jo'),
        new Player(4, 'Kristian', 'K'),
        new Player(5, 'Nico', 'Ni'),
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
}
