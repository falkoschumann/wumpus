'use strict';

// region Backend

/**
 * @typedef {{success: boolean, errorMessage?: string}} CommandStatus
 */

/**
 * @return CommandStatus
 */
function success() {
  return {success: true};
}

/**
 * @param {string} errorMessage
 * @return CommandStatus
 */
function failure(errorMessage) {
  return {success: false, errorMessage};
}

/** @typedef {{state: State}} DetermineGameStateQueryResult */
/** @typedef {{room: number, tunnelsLeadTo: number[], warnings: ('WUMPUS'|'PIT'|'BAT')[]}} ExploreRoomQueryResult */

/** @typedef {('PLAYER'|'WUMPUS'|'PIT_1'|'PIT_2'|'BAT_1'|'BAT_2')} Item */
const ITEM_PLAYER = 'PLAYER';
const ITEM_WUMPUS = 'WUMPUS';
const ITEM_PIT_1 = 'PIT_1';
const ITEM_PIT_2 = 'PIT_2';
const ITEM_BAT_1 = 'BAT_1';
const ITEM_BAT_2 = 'BAT_2';
const ITEMS = Array.of(ITEM_PLAYER, ITEM_WUMPUS, ITEM_PIT_1, ITEM_PIT_2, ITEM_BAT_1, ITEM_BAT_2);

/** @typedef {('OPEN'|'WON'|'LOST')} State */
const STATE_OPEN = 'OPEN';
const STATE_WON = 'WON';
const STATE_LOST = 'LOST';
const STATES = Array.of(STATE_OPEN, STATE_WON, STATE_LOST);

/** @typedef  {{room: number, tunnelsLeadTo: Set<number>}} Room */

class Cave {
  constructor() {
    this._rooms = this._dodecahedron();
    /** @type {Map<Item, number>} */
    this._items = new Map();
  }

  /**
   * @returns {Map<number, Room>}
   * @private
   */
  _dodecahedron() {
    const dodecahedron = new Map();
    dodecahedron.set(1, {room: 1, tunnelsLeadTo: new Set([2, 5, 8])});
    dodecahedron.set(2, {room: 2, tunnelsLeadTo: new Set([1, 3, 10])});
    dodecahedron.set(3, {room: 3, tunnelsLeadTo: new Set([2, 4, 12])});
    dodecahedron.set(4, {room: 4, tunnelsLeadTo: new Set([3, 5, 14])});
    dodecahedron.set(5, {room: 5, tunnelsLeadTo: new Set([1, 4, 6])});
    dodecahedron.set(6, {room: 6, tunnelsLeadTo: new Set([5, 7, 15])});
    dodecahedron.set(7, {room: 7, tunnelsLeadTo: new Set([6, 8, 17])});
    dodecahedron.set(8, {room: 8, tunnelsLeadTo: new Set([1, 7, 9])});
    dodecahedron.set(9, {room: 9, tunnelsLeadTo: new Set([8, 10, 18])});
    dodecahedron.set(10, {room: 10, tunnelsLeadTo: new Set([2, 9, 11])});
    dodecahedron.set(11, {room: 11, tunnelsLeadTo: new Set([10, 12, 19])});
    dodecahedron.set(12, {room: 12, tunnelsLeadTo: new Set([3, 11, 13])});
    dodecahedron.set(13, {room: 13, tunnelsLeadTo: new Set([12, 14, 20])});
    dodecahedron.set(14, {room: 14, tunnelsLeadTo: new Set([4, 13, 15])});
    dodecahedron.set(15, {room: 15, tunnelsLeadTo: new Set([6, 14, 16])});
    dodecahedron.set(16, {room: 16, tunnelsLeadTo: new Set([15, 17, 20])});
    dodecahedron.set(17, {room: 17, tunnelsLeadTo: new Set([7, 16, 18])});
    dodecahedron.set(18, {room: 18, tunnelsLeadTo: new Set([9, 17, 19])});
    dodecahedron.set(19, {room: 19, tunnelsLeadTo: new Set([11, 18, 20])});
    dodecahedron.set(20, {room: 20, tunnelsLeadTo: new Set([13, 16, 19])});
    return dodecahedron;
  }

  /**
   * @param {number} room
   * @returns {Room}
   */
  getRoom(room) {
    return this._rooms.get(room);
  }

  /**
   * @param {Item} item
   * @returns {Room}
   */
  positionOf(item) {
    const room = this._items.get(item);
    return this.getRoom(room);
  }

  /**
   * @param {Item} item
   * @param {number} atRoom
   */
  position(item, atRoom) {
    this._items.set(item, atRoom);
  }
}

/**
 * @property {(event: 'PLAYER_FELL_INTO_PIT'|
 *     'PLAYER_SNATCHED_BY_BAT'|
 *     'PLAYER_BUMPED_WUMPUS'|
 *     'ARROW_MISSED'|
 *     'ARROW_HIT_WUMPUS'|
 *     'ARROW_HIT_PLAYER'|
 *     'WUMPUS_ATE_PLAYERS'
 *   ) => void} onEvent
 */
class MessageHandler {
  constructor() {
    this._cave = new Cave();
    /** @type {Map<Item, number>} */
    this._initialItems = new Map();
    /** @type {State} */
    this._state = STATE_OPEN;
    this._numberOfArrows = 5;
  }

  /**
   * @param {boolean} withSameSetUp
   * @returns CommandStatus
   */
  startGame(withSameSetUp) {
    this._positionItems();
    this._startGame();
    return success();
  }

  /** @private */
  _positionItems() {
    do {
      this._positionPlayer();
      this._positionWumpus();
      this._positionPits();
      this._positionBats();
    } while (this._hasCrossovers());
  }

  /** @private */
  _positionPlayer() {
    this._initialItems.set(ITEM_PLAYER, this._randomRoom());
  }

  /** @private */
  _positionWumpus() {
    this._initialItems.set(ITEM_WUMPUS, this._randomRoom());
  }

  /** @private */
  _positionPits() {
    this._initialItems.set(ITEM_PIT_1, this._randomRoom());
    this._initialItems.set(ITEM_PIT_2, this._randomRoom());
  }

  /** @private */
  _positionBats() {
    this._initialItems.set(ITEM_BAT_1, this._randomRoom());
    this._initialItems.set(ITEM_BAT_2, this._randomRoom());
  }

  /**
   * @return {boolean}
   * @private
   */
  _hasCrossovers() {
    for (const item1 of ITEMS) {
      for (const item2 of ITEMS) {
        if (item1 === item2) {
          continue;
        }

        if (this._initialItems.get(item1) === this._initialItems.get(item2)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return {number}
   * @private
   */
  _randomRoom() {
    const min = 1;
    const max = 21;
    return Math.floor(Math.random() * (max - min) + min);
  }

  /** @private */
  _startGame() {
    this._cave.position(ITEM_PLAYER, this._initialItems.get(ITEM_PLAYER));
    this._cave.position(ITEM_WUMPUS, this._initialItems.get(ITEM_WUMPUS));
    this._cave.position(ITEM_PIT_1, this._initialItems.get(ITEM_PIT_1));
    this._cave.position(ITEM_PIT_2, this._initialItems.get(ITEM_PIT_2));
    this._cave.position(ITEM_BAT_1, this._initialItems.get(ITEM_BAT_1));
    this._cave.position(ITEM_BAT_2, this._initialItems.get(ITEM_BAT_2));
    this._state = STATE_OPEN;
    this._numberOfArrows = 5;
  }

  /**
   * @param {number} toRoom
   * @returns CommandStatus
   */
  movePlayer(toRoom) {
    return success();
  }

  /**
   * @param {number[]} intoRooms
   * @returns CommandStatus
   */
  shootCrookedArrow(intoRooms) {
    return success();
  }

  /**
   * @returns {ExploreRoomQueryResult}
   */
  exploreRoom() {
    return {room: 1, tunnelsLeadTo: [2, 3, 4], warnings: []};
  }

  /**
   * @returns {DetermineGameStateQueryResult}
   */
  determineGameState() {
    return {state: 'OPEN'};
  }
}

// endregion

// region Frontend

class UserInterface {
  /**
   * @param {MessageHandler} messageHandler
   */
  constructor(messageHandler) {
    this._messageHandler = messageHandler;
    this._gameOpen = true;
  }

  run() {
    this._startGame();
  }

  /** @private */
  _startGame(withSameSetUp = false) {
    console.log("Hunt the Wumpus");
    console.log();
    this._messageHandler.startGame(withSameSetUp);
    const result = this._messageHandler.determineGameState();
    this._displayDetermineGameStateQueryResult(result);
  }

  /**
   * @param {DetermineGameStateQueryResult} result
   * @private
   */
  _displayDetermineGameStateQueryResult(result) {
    switch (result.state) {
      case STATE_OPEN:
        this._gameOpen = true;
        break;
      case STATE_LOST:
        console.log("HA HA HA - You lose!");
        this._gameOpen = false;
        break;
      case STATE_WON:
        console.log("HEE HEE HEE - The wumpus'll getcha next time!!");
        this._gameOpen = false;
        break;
    }
  }
}

// endregion

// region App

const backend = new MessageHandler();
const frontend = new UserInterface(backend);
frontend.run();

// endregion
