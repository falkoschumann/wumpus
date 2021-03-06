import java.util.Arrays;
import java.util.InputMismatchException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

// region App

public class Wumpus {
  public static void main(String[] args) {
    var backend = new MessageHandler();
    var frontend = new UserInterface(backend);
    frontend.run();
  }
}

// endregion

// region Contract

record StartGameCommand(boolean sameSetUp) {}

record MovePlayerCommand(int room) {}

record ShootCrookedArrowCommand(List<Integer> rooms) {}

record ExploreRoomQuery() {}

record ExploreRoomQueryResult(int number, Set<Integer> tunnelsLeadTo, Set<Warning> warnings) {
  enum Warning {
    WUMPUS,
    PIT,
    BAT,
  }
}

record DetermineGameStateQuery() {}

record DetermineGameStateQueryResult(State state) {
  enum State {
    OPEN,
    WON,
    LOST,
  }
}

enum GameNotification {
  PLAYER_FELL_INTO_PIT,
  PLAYER_SNATCHED_BY_BAT,
  PLAYER_BUMPED_WUMPUS,
  ARROW_MISSED,
  ARROW_HIT_WUMPUS,
  ARROW_HIT_PLAYER,
  WUMPUS_ATE_PLAYER,
}

// endregion

// region Backend

class MessageHandler {
  Consumer<GameNotification> onEvent;

  private final RandomGenerator random = RandomGenerator.of("Xoroshiro128PlusPlus");

  private final Cave cave = new Cave();
  private final Map<Item, Integer> initialItems = new TreeMap<>();
  private DetermineGameStateQueryResult.State state;
  private int numberOfArrows;

  void handle(StartGameCommand command) {
    if (!command.sameSetUp() || initialItems.isEmpty()) {
      positionItems();
    }
    startGame();
  }

  private void positionItems() {
    do {
      positionPlayer();
      positionWumpus();
      positionPits();
      positionBats();
    } while (hasCrossovers());
  }

  private void positionPlayer() {
    initialItems.put(Item.PLAYER, randomRoom());
  }

  private void positionWumpus() {
    initialItems.put(Item.WUMPUS, randomRoom());
  }

  private void positionPits() {
    initialItems.put(Item.PIT_1, randomRoom());
    initialItems.put(Item.PIT_2, randomRoom());
  }

  private void positionBats() {
    initialItems.put(Item.BAT_1, randomRoom());
    initialItems.put(Item.BAT_2, randomRoom());
  }

  private boolean hasCrossovers() {
    for (var item1 : Item.values()) {
      for (var item2 : Item.values()) {
        if (item1 == item2) {
          continue;
        }
        if (Objects.equals(initialItems.get(item1), initialItems.get(item2))) {
          return true;
        }
      }
    }
    return false;
  }

  private int randomRoom() {
    return random.nextInt(1, 21);
  }

  private void startGame() {
    cave.position(Item.PLAYER, initialItems.get(Item.PLAYER));
    cave.position(Item.WUMPUS, initialItems.get(Item.WUMPUS));
    cave.position(Item.PIT_1, initialItems.get(Item.PIT_1));
    cave.position(Item.PIT_2, initialItems.get(Item.PIT_2));
    cave.position(Item.BAT_1, initialItems.get(Item.BAT_1));
    cave.position(Item.BAT_2, initialItems.get(Item.BAT_2));
    state = DetermineGameStateQueryResult.State.OPEN;
    numberOfArrows = 5;
  }

  void handle(MovePlayerCommand command) {
    cave.position(Item.PLAYER, command.room());
    if (cave.positionOf(Item.WUMPUS).number() == command.room()) {
      onEvent.accept(GameNotification.PLAYER_BUMPED_WUMPUS);
      moveWumpusRandomly();
      checkPlayerAndWumpusInSameRoom();
    } else if (cave.positionOf(Item.PIT_1).number() == command.room()
        || cave.positionOf(Item.PIT_2).number() == command.room()) {
      onEvent.accept(GameNotification.PLAYER_FELL_INTO_PIT);
      state = DetermineGameStateQueryResult.State.LOST;
    } else if (cave.positionOf(Item.BAT_1).number() == command.room()
        || cave.positionOf(Item.BAT_2).number() == command.room()) {
      onEvent.accept(GameNotification.PLAYER_SNATCHED_BY_BAT);
      movePlayerRandomly(command.room());
    }
  }

  private void moveWumpusRandomly() {
    var tunnelIndex = random.nextInt(4);
    if (tunnelIndex < 3) {
      // Wumpus don't stay still
      var tunnels = List.copyOf(cave.positionOf(Item.WUMPUS).tunnelsLeadTo());
      var roomNumber = tunnels.get(tunnelIndex);
      cave.position(Item.WUMPUS, roomNumber);
    }
  }

  private boolean checkPlayerAndWumpusInSameRoom() {
    if (cave.positionOf(Item.WUMPUS).number() == cave.positionOf(Item.PLAYER).number()) {
      onEvent.accept(GameNotification.WUMPUS_ATE_PLAYER);
      state = DetermineGameStateQueryResult.State.LOST;
      return true;
    } else {
      return false;
    }
  }

  private void movePlayerRandomly(int currentRoom) {
    var i = currentRoom;
    do {
      i = randomRoom();
    } while (i == currentRoom);
    handle(new MovePlayerCommand(i));
  }

  void handle(ShootCrookedArrowCommand command) {
    var room = cave.positionOf(Item.PLAYER);
    for (int number : command.rooms()) {
      number = validateNextRoom(room, number);
      room = cave.getRoom(number);

      if (cave.positionOf(Item.WUMPUS).number() == room.number()) {
        onEvent.accept(GameNotification.ARROW_HIT_WUMPUS);
        state = DetermineGameStateQueryResult.State.WON;
        return;
      } else if (cave.positionOf(Item.PLAYER).number() == room.number()) {
        onEvent.accept(GameNotification.ARROW_HIT_PLAYER);
        state = DetermineGameStateQueryResult.State.LOST;
        return;
      } else {
        moveWumpusRandomly();
        if (checkPlayerAndWumpusInSameRoom()) {
          return;
        }
      }
    }

    numberOfArrows--;
    if (numberOfArrows == 0) {
      state = DetermineGameStateQueryResult.State.LOST;
    }
  }

  private int validateNextRoom(Room current, int next) {
    if (!current.tunnelsLeadTo().contains(next)) {
      var tunnelIndex = randomTunnelIndex();
      var tunnels = List.copyOf(current.tunnelsLeadTo());
      next = tunnels.get(tunnelIndex);
    }
    return next;
  }

  private int randomTunnelIndex() {
    return random.nextInt(3);
  }

  ExploreRoomQueryResult handle(ExploreRoomQuery query) {
    var room = currentRoom();
    var warnings = checkRooms(room.tunnelsLeadTo());
    return new ExploreRoomQueryResult(room.number(), room.tunnelsLeadTo(), warnings);
  }

  private Room currentRoom() {
    return cave.positionOf(Item.PLAYER);
  }

  private Set<ExploreRoomQueryResult.Warning> checkRooms(Set<Integer> numbers) {
    var warnings = new LinkedHashSet<ExploreRoomQueryResult.Warning>();
    if (numbers.contains(cave.positionOf(Item.WUMPUS).number())) {
      warnings.add(ExploreRoomQueryResult.Warning.WUMPUS);
    }
    if (numbers.contains(cave.positionOf(Item.PIT_1).number())
        || numbers.contains(cave.positionOf(Item.PIT_2).number())) {
      warnings.add(ExploreRoomQueryResult.Warning.PIT);
    }
    if (numbers.contains(cave.positionOf(Item.BAT_1).number())
        || numbers.contains(cave.positionOf(Item.BAT_2).number())) {
      warnings.add(ExploreRoomQueryResult.Warning.BAT);
    }
    return Set.copyOf(warnings);
  }

  DetermineGameStateQueryResult handle(DetermineGameStateQuery query) {
    return new DetermineGameStateQueryResult(state);
  }

  private class Cave {
    private final Map<Integer, Room> rooms;
    private final Map<Item, Integer> items = new TreeMap<>();

    Cave() {
      var dodecahedron = new LinkedHashMap<Integer, Room>();
      dodecahedron.put(1, new Room(1, Set.of(2, 5, 8)));
      dodecahedron.put(2, new Room(2, Set.of(1, 3, 10)));
      dodecahedron.put(3, new Room(3, Set.of(2, 4, 12)));
      dodecahedron.put(4, new Room(4, Set.of(3, 5, 14)));
      dodecahedron.put(5, new Room(5, Set.of(1, 4, 6)));
      dodecahedron.put(6, new Room(6, Set.of(5, 7, 15)));
      dodecahedron.put(7, new Room(7, Set.of(6, 8, 17)));
      dodecahedron.put(8, new Room(8, Set.of(1, 7, 9)));
      dodecahedron.put(9, new Room(9, Set.of(8, 10, 18)));
      dodecahedron.put(10, new Room(10, Set.of(2, 9, 11)));
      dodecahedron.put(11, new Room(11, Set.of(10, 12, 19)));
      dodecahedron.put(12, new Room(12, Set.of(3, 11, 13)));
      dodecahedron.put(13, new Room(13, Set.of(12, 14, 20)));
      dodecahedron.put(14, new Room(14, Set.of(4, 13, 15)));
      dodecahedron.put(15, new Room(15, Set.of(6, 14, 16)));
      dodecahedron.put(16, new Room(16, Set.of(15, 17, 20)));
      dodecahedron.put(17, new Room(17, Set.of(7, 16, 18)));
      dodecahedron.put(18, new Room(18, Set.of(9, 17, 19)));
      dodecahedron.put(19, new Room(19, Set.of(11, 18, 20)));
      dodecahedron.put(20, new Room(20, Set.of(13, 16, 19)));
      rooms = Map.copyOf(dodecahedron);
    }

    Room getRoom(Integer number) {
      return rooms.get(number);
    }

    Room positionOf(Item item) {
      var number = items.get(item);
      return getRoom(number);
    }

    void position(Item item, int atRoom) {
      items.put(item, atRoom);
    }
  }

  private record Room(int number, Set<Integer> tunnelsLeadTo) {}

  private enum Item {
    PLAYER,
    WUMPUS,
    PIT_1,
    PIT_2,
    BAT_1,
    BAT_2,
  }
}

// endregion

// region Frontend

class UserInterface {
  private final Scanner input = new Scanner(System.in);
  private final MessageHandler messageHandler;
  private int currentRoom;
  private Set<Integer> tunnelsLeadTo;
  private boolean gameOpen = true;

  UserInterface(MessageHandler messageHandler) {
    this.messageHandler = messageHandler;

    messageHandler.onEvent = this::display;
  }

  void run() {
    showInstructions();
    startGame();
    while (true) {
      while (isGameOpen()) {
        exploreRoom();
        switch (action()) {
          case SHOOT -> shootCrookedArrow();
          case MOVE -> movePlayer();
        }
      }
      restartGame();
    }
  }

  private void showInstructions() {
    System.out.print("Instructions (y/n) ");
    var s = (String) input.next();
    if (!Objects.equals(s, "y")) {
      System.out.println();
      return;
    }

    System.out.println("""
      Welcome to 'Hunt the Wumpus'

        The wumpus lives in a cave of 20 rooms. each room has 3 tunnels leading
      to other rooms. (Look at a dodecahedron to see how this works-if you don't
      know what a dodecahedron is, ask someone)

      Hazards:

      Bottomless pits - two rooms have bottomless pits in them if you go there,
          you fall into the pit (& lose!)
      Super bats - two other rooms have super bats. If you go there, a bat
          grabs you and takes you to some other room at random. (Which might be
          troublesome)

      Wumpus:

        The wumpus is not bothered by the hazards (he has sucker feet and is too
      big for a bat to lift). Usually he is asleep. Two things wake him up: your
      entering his room or your shooting an arrow.
        If the wumpus wakes, he moves (p=0.75) one room or stays still (p=0.25).
      After that, if he is where you are, he eats you up (& you lose!)

      You:

        Each turn you may move or shoot a crooked arrow
      moving: you can go one room (thru one tunnel)
      arrows: you have 5 arrows. you lose when you run out. Each arrow can go
          from 1 to 5 rooms. You aim by telling the computer the room#s you want
          the arrow to go to.
          If the arrow can't go that way (ie no tunnel) it moves at random to
          the next room.
          If the arrow hits the wumpus, you win.
          If the arrow hits you, you lose.

      Warnings:

        When you are one room away from wumpus or hazard, the computer says:
      Wumpus - 'I smell a wumpus'
      Bat    - 'Bats nearby'
      Pit    - 'I feel a draft'
      """);
  }

  private void startGame() {
    startGame(false);
  }

  private void startGame(boolean sameSetUp) {
    System.out.println("Hunt the Wumpus");
    System.out.println();
    messageHandler.handle(new StartGameCommand(sameSetUp));
    var result = messageHandler.handle(new DetermineGameStateQuery());
    display(result);
  }

  private boolean isGameOpen() {
    return gameOpen;
  }

  private void exploreRoom() {
    var result = messageHandler.handle(new ExploreRoomQuery());
    display(result);
  }

  private Action action() {
    do {
      System.out.print("Shoot or move (s/m) ");
      var s = (String) input.next();
      if (Objects.equals(s, "s")) {
        return Action.SHOOT;
      } else if (Objects.equals(s, "m")) {
        return Action.MOVE;
      }
      System.out.println();
    } while (true);
  }

  private void shootCrookedArrow() {
    var numberOfRooms = 0;
    do {
      System.out.print("No. of rooms (1-5) ");
      numberOfRooms = input.nextInt();
    } while (numberOfRooms < 1 || numberOfRooms > 5);

    var path = new int[numberOfRooms];
    for (var i = 0; i < numberOfRooms; i++) {
      System.out.print("Room # ");
      path[i] = input.nextInt();

      if (i >= 2 && path[i] == path[i-2]) {
        System.out.println("Arrows aren't that crooked - Try another room");
        i--;
      }
    }
    System.out.println();

    var pathList = Arrays.stream(path).boxed().toList();
    messageHandler.handle(new ShootCrookedArrowCommand(pathList));
    var result = messageHandler.handle(new DetermineGameStateQuery());
    display(result);
  }

  private void movePlayer() {
    while (true) {
      try {
        System.out.print("Where to ");
        var i = input.nextInt();
        if (tunnelsLeadTo.contains(i)) {
          messageHandler.handle(new MovePlayerCommand(i));
          var result = messageHandler.handle(new DetermineGameStateQuery());
          display(result);
          break;
        }
      } catch (InputMismatchException e) {
        input.next();
      }
      System.out.println("Not possible");
    }
    System.out.println();
  }

  private void display(ExploreRoomQueryResult result) {
    currentRoom = result.number();
    tunnelsLeadTo = result.tunnelsLeadTo();
    printRoom();
    printTunnels();
    printWarnings(result.warnings());
    System.out.println();
  }

  private void printRoom() {
    System.out.println("You are in room " + currentRoom);
  }

  private void printTunnels() {
    var tunnels = tunnelsLeadTo.stream()
      .map(String::valueOf)
      .collect(Collectors.joining(", "));
    System.out.println("Tunnels lead to " + tunnels);
  }

  private void printWarnings(Set<ExploreRoomQueryResult.Warning> warnings) {
    for (var e : warnings) {
      switch (e) {
        case WUMPUS -> System.out.println("I smell a Wumpus!");
        case PIT -> System.out.println("I feel a draft");
        case BAT -> System.out.println("Bats nearby!");
      }
    }
  }

  private void restartGame() {
    System.out.print("Same set-up (y/n) ");
    var s = (String) input.next();
    var sameSetUp = Objects.equals(s, "y");
    startGame(sameSetUp);
    System.out.println();
  }

  private void display(DetermineGameStateQueryResult result) {
    switch (result.state()) {
      case OPEN -> gameOpen = true;
      case LOST -> {
        System.out.println("HA HA HA - You lose!");
        gameOpen = false;
      }
      case WON -> {
        System.out.println("HEE HEE HEE - The wumpus'll getcha next time!!");
        gameOpen = false;
      }
    }
  }

  private void display(GameNotification event) {
    switch (event) {
      case PLAYER_FELL_INTO_PIT -> System.out.println("YYYIIIIEEEE ... fell in pit");
      case PLAYER_SNATCHED_BY_BAT ->
        System.out.println("ZAP - Super bat snatch! Elsewhereville for you!");
      case PLAYER_BUMPED_WUMPUS -> System.out.println("...OOPS! Bumped a Wumpus!");
      case ARROW_MISSED -> System.out.println("Missed");
      case ARROW_HIT_WUMPUS -> System.out.println("AHA! You got the Wumpus!");
      case ARROW_HIT_PLAYER -> System.out.println("OUCH! Arrow got you!");
      case WUMPUS_ATE_PLAYER -> System.out.println("TSK TSK TSK - Wumpus got you!");
    }
  }

  private enum Action {
    SHOOT,
    MOVE,
  }
}

// endregion
