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
}
