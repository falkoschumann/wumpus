"""Hunt the Wumpus.

Original by Gregory Yob, 1973.
"""

import random


class Game:
    def __init__(self):
        # cave: 20 rooms, 3 tunnels per room
        self.s = [
            [1, 4, 7],  # 0
            [0, 2, 9],  # 1
            [1, 3, 11],  # 2
            [2, 4, 13],  # 3
            [0, 3, 5],  # 4
            [4, 6, 14],  # 5
            [5, 7, 16],  # 6
            [0, 6, 8],  # 7
            [7, 9, 17],  # 8
            [1, 8, 10],  # 9
            [9, 11, 18],  # 10
            [2, 10, 12],  # 11
            [11, 13, 19],  # 12
            [3, 12, 14],  # 13
            [5, 13, 15],  # 14
            [14, 16, 19],  # 15
            [6, 15, 17],  # 16
            [8, 16, 18],  # 17
            [10, 17, 19],  # 18
            [12, 15, 18],  # 19
        ]
        self.l = []  # rooms: 0 you, 1 wumpus, 2 & 3 pits, 4 & 5 bats
        self.m = []  # memory of l
        self.a = 0  # number of arrows
        self.f = 0

    def _fna(self):
        return random.randint(0, 19)

    def _fnb(self):
        return random.randint(0, 2)

    def _fnc(self):
        return random.randint(0, 3)

    def locate_items(self):
        self.l = [0, 0, 0, 0, 0, 0]
        while self._check_for_crossovers():
            for j in range(0, 6):
                self.l[j] = self._fna()
        self.m = self.l.copy()

    def _check_for_crossovers(self):
        for j in range(0, 6):
            for k in range(0, 6):
                if j != k and self.l[j] == self.l[k]:
                    return True
        return False

    def set_number_of_arrows(self):
        self.a = 5

    def instructions(self):
        print("Welcome to 'Hunt the Wumpus'")
        print("  The wumpus lives in a cave of 20 rooms. each room")
        print("has 3 tunnels leading to other rooms. (Look at a")
        print("dodecahedron to see how this works-if you don't know")
        print("what a dodecahedron is, ask someone)")
        print()
        print("     Hazards:")
        print(" Bottomless pits - Two rooms have bottomless pits in them")
        print("     if you go there, you fall into the pit (& lose!)")
        print(" Super bats - Two other rooms have super bats. If you")
        print("     go there, a bat grabs you and takes you to some other")
        print("     room at random. (Which might be troublesome)")
        print()
        print("     Wumpus:")
        print(" The wumpus is not bothered by the hazards (he has sucker")
        print(" feet and is too big for a bat to lift).  Usually")
        print(" he is asleep. Two things wake him up: your entering")
        print(" his room or your shooting an arrow.")
        print("     If the wumpus wakes, he moves (p=.75) one room")
        print(" or stays still (p=.25). After that, if he is where you")
        print(" are, he eats you up (& you lose!)")
        print()
        print("     You:")
        print(" Each turn you may move or shoot a crooked arrow")
        print("   moving: you can go one room (thru one tunnel)")
        print("   arrows: you have 5 arrows. You lose when you run out.")
        print("   each arrow can go from 1 to 5 rooms. You aim by telling")
        print("   the computer the room#s you want the arrow to go to.")
        print("   If the arrow can't go that way (ie no tunnel) it moves")
        print("   at ramdom to the next room.")
        print("     If the arrow hits the wumpus, you win.")
        print("     If the arrow hits you, you lose.")
        print()
        print("    Warnings:")
        print("     When you are one room away from wumpus or hazard,")
        print("    the computer says:")
        print(" Wumpus -  'I smell a wumpus'")
        print(" Bat    -  'Bats nearby'")
        print(" Pit    -  'I feel a draft'")
        print()

    def print_location_and_hazard_warnings(self):
        print()
        for j in range(1, 6):
            for k in range(0, 3):
                if self.s[self.l[0]][k] != self.l[j]:
                    continue
                if j == 1:
                    print("I smell a wumpus")
                elif j == 2 or j == 3:
                    print("I feel a draft")
                else:
                    print("Bats nearby")
        print("You are in room", self.l[0])
        print(
            "Tunnels lead to rooms",
            self.s[self.l[0]][0],
            self.s[self.l[0]][1],
            self.s[self.l[0]][2],
        )
        print()

    def choose_option(self):
        while True:
            i = input("Shoot or move (s/m)? ")
            if i == "s":
                self.arrow()
                break
            if i == "m":
                self.move()
                break

    def arrow(self):
        self.f = 0
        # path of arrow
        p = [0, 0, 0, 0, 0]
        while True:
            j9 = int(input("Number of rooms (1-5)? "))
            if j9 < 1 or j9 > 5:
                continue
            for k in range(0, j9):
                while True:
                    p[k] = int(input("Room number? "))
                    if k <= 1 or p[k] != p[k - 2]:
                        break
                    print("Arrows aren't that crooked - try another room")
            break
        # shoot arrow
        l = self.l[0]
        for k in range(0, j9):
            for k1 in range(0, 3):
                if self.s[l][k1] == p[k]:
                    l = p[k]
                    self._see_if_arrow_hits_user_or_wumpus(l)
                    if self.f != 0:
                        return
            # no tunnel for arrow
            l = self.s[l][self._fnb()]
            self._see_if_arrow_hits_user_or_wumpus(l)
            if self.f != 0:
                return
        print("Missed")
        self._move_wumpus()
        # ammo check
        self.a -= 1
        if self.a == 0:
            self.f = -1

    def _move_wumpus(self):
        k = self._fnc()
        if k == 3:
            if self.l[0] == self.l[1]:
                print("Tsk tsk tsk - Wumpus got you!")
                self.f = -1
        else:
            self.l[1] = self.s[self.l[1]][k]

    def _see_if_arrow_hits_user_or_wumpus(self, l):
        if l == self.l[1]:
            print("Aha! You got the Wumpus!")
            self.f = 1
        elif l == self.l[0]:
            print("Ouch! Arrow got you!")
            self.f = -1

    def move(self):
        self.f = 0
        while True:
            l = int(input("Where to? "))
            if l < 0 or l > 19:
                continue
            if self._check_if_legal_move(l) or l == self.l[0]:
                break
            print("Not possible -")
        # check for hazards
        while True:
            self.l[0] = l
            # wumpus
            if l == self.l[1]:
                print("...Oops! Bumped a Wumpus!")
                self._move_wumpus()
                if self.f != 0:
                    return
            # pit
            if l == self.l[2] or l == self.l[3]:
                print("YYYIIIIEEEE . . . Fell in pit")
                self.f = -1
                return
            # bats
            if l == self.l[4] or l == self.l[5]:
                print("ZAP--Super bat snatch! Elsewhereville for you!")
                self.l[0] = self._fna()
                continue
            break

    def _check_if_legal_move(self, l):
        for k in range(0, 3):
            if self.s[self.l[0]][k] == l:
                return True
        return False


def main():
    game = Game()
    i = input("Instructions (y/n)? ")
    if i != "n":
        game.instructions()
    game.locate_items()
    game.set_number_of_arrows()
    print(game.l)

    # Start the game
    print("Hunt the Wumpus")
    game.print_location_and_hazard_warnings()
    game.choose_option()


if __name__ == "__main__":
    main()
