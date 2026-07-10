

function doEngineInit() {
	// startup LittleJS with your game functions after the tile image is loaded
	engineInit(gameInit, gameUpdate, gameRenderPost);
}


function gameInit() {
	

	GLOBAL.uiFont = new FontImage(GLOBAL.fontImage);

	GLOBAL.mapMan = new MapManager();

	// UI
	GLOBAL.trainMenu.push(
		new Button_Train(128, 96, tile(12), tile(vec2(72, 72), 24), () => {
			for (let i = 0; i < GLOBAL.units.length; i++) {
				const unit = GLOBAL.units[i];
				if (unit.selected) {
					GLOBAL.units.splice(GLOBAL.units.indexOf(unit), 1);
					const newUnit = new Unit_Archer(unit.pos);
					newUnit.shelter = unit.shelter;
					GLOBAL.units.push(newUnit);
					unit.destroy();
					GLOBAL.state = 0;
				}
			}
		}),
		new Button_Train(256, 96, tile(14), tile(vec2(0, 24), 24), () => {
			for (let i = 0; i < GLOBAL.units.length; i++) {
				const unit = GLOBAL.units[i];
				if (unit.selected) {
					GLOBAL.units.splice(GLOBAL.units.indexOf(unit), 1);
					const newUnit = new Unit_Soldier(unit.pos);
					newUnit.shelter = unit.shelter;
					GLOBAL.units.push(newUnit);
					unit.destroy();
					GLOBAL.state = 0;
				}
			}
		}),
	);
	const dx = min(128, Math.round(128 * innerWidth / 900));

	GLOBAL.buildMenu.push(
		new Button_Build(dx, 96, tile(50), 6, 4, 0, () => {
			GLOBAL.showMessage('HOUSE IS LIVING SPACE');
			GLOBAL.state = DEFS.STATES.BUILD_HOUSE;
		}),
		new Button_Build(dx*2, 96, tile(vec2(24, 96), vec2(24)), 6, 4, 0, () => {
			GLOBAL.showMessage('FARM IS FOR FOOD');
			GLOBAL.state = DEFS.STATES.BUILD_FARM;
		}),
		new Button_Build(dx*3, 96, tile(51), 2, 1, 0, () => {
			GLOBAL.showMessage('WALL IS FOR PROTECTION');
			GLOBAL.state = DEFS.STATES.BUILD_WALL;
		}),
		new Button_Build(dx*4, 96, tile(vec2(0, 96), vec2(24)), 6, 10, 0, () => {
			//GLOBAL.showMessage('the quick brown\nfox! jumps over\nthe lazy dog?');
			GLOBAL.showMessage('PUT WORKERS INTO\nBARRACKS TO TRAIN');
			GLOBAL.state = DEFS.STATES.BUILD_BARRACKS;
		}),
	);
	GLOBAL.townHallMenu.push(
		new Button_CreateWorker(128, 96, tile(4), () => {
			GLOBAL.food -= 5 * GLOBAL.units.length;
			GLOBAL.units.push(new PlayerUnit(DEFS.HOME.add(vec2(rand(-1, 1), - 1))));
			GLOBAL.speak(rand() > .5 ? 'hi' : 'hello?');
		})
	);

	GLOBAL.spellMenu.push(
		new Button_Spell(innerWidth - dx, innerHeight - 192, tile(90), 5, () => {
			// create food
			GLOBAL.mana -= 5;
			GLOBAL.food += 10;
			zzfx(...[.9, , 588, , .03, .14, , 3.9, , -1, 650, .05, .04, , , , .08, .84, .3]);
			GLOBAL.showMessage('FOOD CREATED');
		}),
		new Button_Spell(innerWidth - dx, innerHeight - (192 + dx), tile(91), 8, () => {
			// healing
			GLOBAL.mana -= 8;
			for (let i = 0; i < GLOBAL.units.length; i++) {
				const unit = GLOBAL.units[i];
				if (unit.hitPoints < unit.maxHitPoints) {
					GLOBAL.vfxMan.addParticles(unit.pos, GLOBAL.vfxMan.heartPlusses);
					unit.hitPoints = min(unit.maxHitPoints, unit.hitPoints + 2);
				}
			}
			for (let i = 0; i < GLOBAL.buildings.length; i++) {
				const unit = GLOBAL.buildings[i];
				if (unit.hitPoints < unit.maxHitPoints) {
					GLOBAL.vfxMan.addParticles(unit.pos, GLOBAL.vfxMan.heartPlusses);
					unit.hitPoints = min(unit.maxHitPoints, unit.hitPoints + 2);
				}
			}
			zzfx(...[, , 244, , .05, .32, , 1.7, -2, , 421, .09, .02, , , , , .8, .15]);
			GLOBAL.showMessage('FRIENDS ARE HEALED');
		}),
		new Button_Spell(innerWidth - dx, innerHeight - (192 + dx * 2), tile(89), 10, () => {
			// poison
			GLOBAL.mana -= 10;
			for (let i = 0; i < GLOBAL.enemies.length; i++) {
				GLOBAL.vfxMan.addParticles(GLOBAL.enemies[i].pos, GLOBAL.vfxMan.gasPlumes);
				GLOBAL.enemies[i].takeDamage(1);
			}
			zzfx(...[.7,,530,.01,.14,.13,,.3,-10,,,,,,32,,.03,.3,.1,,346]);
			GLOBAL.showMessage('ENEMIES ARE SICKENED');
		}),
		new Button_Spell(innerWidth - dx, innerHeight - (192 + dx * 3), tile(88), 20, () => {
			// lightning
			GLOBAL.mana -= 20;
			for (let i = 0; i < GLOBAL.enemies.length; i++) {
				GLOBAL.vfxMan.addParticles(GLOBAL.enemies[i].pos, GLOBAL.vfxMan.sparks);
				GLOBAL.enemies[i].takeDamage(GLOBAL.enemies.length < 2 ? 4 : 2);
			}
			zzfx(...[2.8, , 48, , .23, .73, 4, 1.9, , -4, , , .21, .2, , .5, .41, .36, .07, .29]);
			GLOBAL.showMessage('ENEMIES ARE SMITED');
		})
	);

	GLOBAL.desiredCameraPos = cameraPos = DEFS.HOME;
	cameraScale = min(60, 60 * innerWidth / 700);

	GLOBAL.warriorIndex = 0;
	//GLOBAL.warriorTimer = new Timer(5);
	GLOBAL.warriorTimer = new Timer(150);

}
function gameUpdate() {

	if (GLOBAL.musicPlaying == GLOBAL.music && GLOBAL.enemies.length) {
		GLOBAL.music.source.stop();
		GLOBAL.music2.playMusic(1, true);
		GLOBAL.musicPlaying = GLOBAL.music2;
	}
	else if (GLOBAL.musicPlaying == GLOBAL.music2 && !GLOBAL.enemies.length) {
		GLOBAL.music2.source.stop();
		GLOBAL.music.playMusic(1, true);
		GLOBAL.musicPlaying = GLOBAL.music;
	}


	if (GLOBAL.state == DEFS.STATES.GAME_LOST) {

		if (mouseIsDown(0)) {
			clearInput();
			setTimeout(location.reload.bind(location), 2);
		}

		return;
	}
	else if (GLOBAL.state == DEFS.STATES.GAME_WON) {

		return;
	}

	if (GLOBAL.warriorIndex > 12 && !GLOBAL.enemies.length) {
		// game is won
		GLOBAL.state = DEFS.STATES.GAME_WON;

		GLOBAL.origCameraScale = cameraScale;
		GLOBAL.maxCameraScale = cameraScale * 2;
		GLOBAL.minCameraScale = cameraScale / 2;
		GLOBAL.dScale = -cameraScale / 100;

		return;
	}

	if (GLOBAL.state > 5) {
		// building

		if (mouseIsDown(0)) {
			clearInput();

			if (!GLOBAL.mapMan.getTileAt(mousePos)) {
				// legal position
				const x = Math.round(mousePos.x);
				const y = Math.round(mousePos.y);

				if (GLOBAL.state == DEFS.STATES.BUILD_BARRACKS) {
					GLOBAL.buildings.push(new Building_Barracks(vec2(x, y)));
				}
				else if (GLOBAL.state == DEFS.STATES.BUILD_FARM) {
					GLOBAL.buildings.push(new Building_Farm(vec2(x, y)));
				}
				else if (GLOBAL.state == DEFS.STATES.BUILD_WALL) {
					GLOBAL.wood -= 2;
					GLOBAL.stone -= 1;
					GLOBAL.buildings.push(new Building(vec2(x, y), 1, tile(51)));
				}
				else {
					// house
					buildHouse(vec2(x, y));
				}
				
			}
			
			GLOBAL.state = 0;
		}
	}
	else {
		GLOBAL.inputMan.update();
	}

	GLOBAL.vfxMan.update();

	if (GLOBAL.warriorTimer.isSet() && GLOBAL.warriorTimer.elapsed()) {

		const def = DEFS.WARRIORS[GLOBAL.warriorIndex];

		GLOBAL.speak('The' + def.number + ' warrior, ' + def.name + ' approaches', 2, 1, 1);
		//GLOBAL.showMessage(def.name + '\nHAS LANDED IN THE \n' + def.from);
		
		// spawn enemies
		for (let i = 0; i < def.enemies.length; i += 2) {

			let
				tileInfo = tile(94 + randInt(0, 4) * 8),
				size,
				hitPoints = GLOBAL.warriorIndex > 9 ? 4 : 3;

			if (i == 0) {
				// hero
				tileInfo = tile(def.heroTile || 6);
				size = vec2(def.heroSize || 1.2);
				// king gets double bonus
				hitPoints += GLOBAL.warriorIndex == 12 ? 12 : Math.floor(GLOBAL.warriorIndex / 2);
			}
			let enemy = new Unit_Enemy(vec2(def.enemies[i], def.enemies[i+1]), size, tileInfo, hitPoints);
			enemy.destination = DEFS.HOME;
			GLOBAL.enemies.push(enemy);

		}

		// place boat
		const
			x = def.enemies[0],
			y = def.enemies[1];
		GLOBAL.boat.pos = vec2(x > 20 ? 33 : x < 5 ? 2 : 18, y > 20 ? 33 : y < 5 ? 2 : 18);

		
		if (GLOBAL.warriorIndex < 12) {
			//GLOBAL.warriorTimer.set(35);
			GLOBAL.warriorTimer.set(90);
		}
		else {
			GLOBAL.warriorTimer.unset();
		}

		GLOBAL.warriorIndex++;
	}

	// occasionally push units apart
	separateUnits(GLOBAL.units);
	separateUnits(GLOBAL.enemies);

	// lerp camera
	if (cameraPos != GLOBAL.desiredCameraPos) {
		const diff = GLOBAL.desiredCameraPos.subtract(cameraPos);
		if (diff.length() < .2) {
			GLOBAL.desiredCameraPos = cameraPos;
		}
		cameraPos = cameraPos.add(diff.clampLength(diff.length() / 10));
	}

}

function buildHouse(pos) {
	const building = new Building(pos, vec2(1), tile(50));
	building.popSupport = 2;
	building.smokePos = building.pos.add(vec2(.3, .5));
	GLOBAL.wood -= 6;
	GLOBAL.stone -= 4;
	GLOBAL.buildings.push(building);
	return building;
}


function separateUnits(unitArray) {

	if (unitArray.length > 1) {
		const index = frame % unitArray.length;
		
		for (let i = 0; i < unitArray.length; i++) {
			if (i != index) {
				let
					unit1 = unitArray[index],
					unit2 = unitArray[i];
				if (i < index) {
					// swap places
					const tmp = unit1;
					unit1 = unit2;
					unit2 = tmp;
				}
				const diff = unit2.pos.subtract(unit1.pos);
				if (diff.length() < .8) {
					// push units away from each other
					if (!unit1.shelter) {
						unit1.pos = unit1.pos.subtract(diff.clampLength(.002));
					}
					if (!unit2.shelter) {
						unit2.pos = unit2.pos.add(diff.clampLength(.002));
					}
					
				}
			}
		}
	}

}

function loadMapData(callback) {

		const img = new Image;
		img.onload = function() {
			try {
			let canvas = document.createElement("canvas");
			canvas.width = img.width;
			canvas.height = img.height;
			let ctx = canvas.getContext("2d");

			ctx.drawImage(img, 0, 0);

			const data = ctx.getImageData(0, 144, 36, 36).data;

			// create map grid from image data
			GLOBAL.mapGrid = [];
			for (let y = 0; y < 36; y++) {
				GLOBAL.mapGrid[35 - y] = [];
				for (let x = 0; x < 36; x++) {
					const index = 4 * (y * 36 + x);
					let val = 0;
					if (data[index + 3] == 0
						|| (data[index] == 91 && data[index + 1] == 110 && data[index + 2] == 225)
						|| (data[index] == 34 && data[index + 1] == 32 && data[index + 2] == 52)) {
						val = 'w';
					}
					else if (data[index] == 75 && data[index + 1] == 105 && data[index + 2] == 47) {
						val = 't';
					}
					else if (data[index] == 155 && data[index + 1] == 173 && data[index + 2] == 183) {
						val = 's';
					}

					GLOBAL.mapGrid[35 - y][x] = val;
				}
			}

			// font
			const fontData = ctx.getImageData(0, 180, 96, 24);
			canvas = document.createElement("canvas");
			canvas.height = 24;
			canvas.width = 96;
			ctx = canvas.getContext("2d");
			ctx.putImageData(fontData, 0, 0);
			GLOBAL.fontImage = document.createElement('img');
			GLOBAL.fontImage.src = canvas.toDataURL("image/png");

			callback();
			} catch (error) {
				document.body.innerHTML =
					'<pre style="color:white;padding:16px;font:14px monospace;">' +
					'Wendol Village map load failed:\n' + error.message +
					'</pre>';
			}
		};
		img.onerror = function () {
			if (!img.__wendolRetried && window.__WENDOL_TEXTURE_DATA_URL) {
				img.__wendolRetried = true;
				img.src = window.__WENDOL_TEXTURE_DATA_URL;
			}
		};
		img.src = window.wendolTextureSrc ? window.wendolTextureSrc() : 't.png';
}

/**
 * static definition variables
 */

const DEFS = {

	HOME: vec2(15),

	STATES: {
		// idle: 0
		GAME_LOST: 1,
		GAME_WON: 2,
		TOWNHALL_MENU: 3,
		TRAIN_MENU: 4,
		BUILD_MENU: 5,
		BUILD_BARRACKS: 6,
		BUILD_HOUSE: 7,
		BUILD_FARM: 8,
		BUILD_WALL: 9
	},

	WARRIORS: [
		{
			number: 'FIRST',
			name: 'HALTAF THE YOUNG',
			heroTile: 102,
			from: 'NORTHEAST',
			enemies: [32, 32]
		},
		{
			number: 'SECOND',
			name: 'HYGLAK THE COMBATIVE',
			from: 'EAST',
			enemies: [32, 18, 30, 17]
		},
		{
			number: 'THIRD',
			name: 'HALGA THE WISE',
			heroTile: 118,
			from: 'SOUTHEAST',
			enemies: [32, 3, 30, 4, 32, 4]
		},
		{
			number: 'FOURTH',
			name: 'HELFDANE THE FAT',
			heroTile: 110,
			from: 'SOUTH',
			enemies: [18, 3, 17, 4, 19, 4]
		},
		{
			number: 'FIFTH',
			name: 'EDGTHO THE SILENT',
			heroTile: 102,
			from: 'SOUTHWEST',
			enemies: [3, 3, 4, 4, 5, 4, 6, 6]
		},
		{
			number: 'SIXTH',
			name: 'RAGNAR THE GRIM',
			heroTile: 94,
			from: 'WEST',
			enemies: [3, 18, 4, 19, 5, 20, 6, 21]
		},
		{
			number: 'SEVENTH',
			name: 'RETHEL THE ARCHER',
			from: 'NORTHWEST',
			enemies: [3, 32, 4, 30, 5, 31, 6, 30]
		},
		{
			number: 'EIGTH',
			name: 'HERGER THE JOYOUS',
			from: 'NORTH',
			enemies: [18, 32, 19, 30, 18, 31, 19, 31]
		},
		{
			number: 'NINTH',
			name: 'WEATH THE MUSICAL',
			from: 'NORTHEAST',
			enemies: [32, 32, 30, 30, 32, 30, 31, 31, 32, 31]
		},
		{
			number: 'TENTH',
			name: 'SKELD THE WARY',
			from: 'EAST',
			enemies: [32, 18, 30, 17, 32, 17, 32, 19, 30, 18]
		},
		{
			number: 'ELEVENTH',
			name: 'RONETH THE QUICK',
			from: 'SOUTHEAST',
			enemies: [32, 3, 30, 4, 32, 4, 31, 4, 31, 3]
		},
		{
			number: 'TWELFTH',
			name: 'EBEN FADLUN',
			heroTile: 108,
			from: 'SOUTH',
			enemies: [18, 3, 17, 4, 19, 4, 18, 4, 17, 5]
		},
		{
			number: 'THIRTEENTH',
			name: 'BEOWULF THE KING',
			heroTile: 116,
			from: 'SOUTHWEST',
			enemies: [3, 3, 4, 4, 5, 4, 4, 19, 5, 20, 6, 21, 17, 4, 19, 4, 18, 4, 17, 5]
		},
		
	]

};


/**
 * global variables
 */

const GLOBAL = {

	trees: [],
	stones: [],
	units: [],
	enemies: [],
	buildings: [],
	boat: undefined,

	// UI
	trainMenu: [],
	buildMenu: [],
	townHallMenu: [],
	spellMenu: [],

	state: 0,

	wood: 24,
	stone: 16,
	food: 5,
	mana: 5,

	voiceIndex: 47,
	phrases: {},
	voices: [],

	messageTimer: new Timer(),
	message: '',

	// for win screen
	origCameraScale: 0,
	maxCameraScale: 0,
	minCameraScale: 0,
	dScale: 0,
	desiredCameraPos: cameraPos,

	// for selection box
	startSelect: undefined,

	countWorkers () {

		let count = 0;
		for (let i = 0; i < GLOBAL.units.length; i++) {
			if (!GLOBAL.units[i].weapon) {
				count++;
			}
		}
		return count;
	},

	getSupportedPop () {
		let supported = 0;
		for (let i = 0; i < GLOBAL.buildings.length; i++) {
			const building = GLOBAL.buildings[i];

			supported += building.needsBuilt ? 0 : building.popSupport;
		}
		return supported;
	},

	speak (phrase, voiceIndex, pitch, rate) {

		/*if (!GLOBAL.voicesLoaded) {
			return;
		}*/

		if (typeof speechSynthesis === 'undefined' || typeof SpeechSynthesisUtterance === 'undefined') {
			return;
		}

		phrase = phrase.toLowerCase();

		const T2S = speechSynthesis;
		var utter = GLOBAL.phrases[phrase] || new SpeechSynthesisUtterance(phrase);
		GLOBAL.phrases[phrase] = utter;

		// 36 Rocko
		// 47 Zarvox
		//const voices = T2S.getVoices();
		// TODO: voices on mobile and safari/firefox
		voiceIndex = voiceIndex || GLOBAL.voiceIndex || 47;
		// check voice available, choose random if not
		voiceIndex = voiceIndex > GLOBAL.voices.length - 1 ? randInt(0, GLOBAL.voices.length) : voiceIndex;

		const voices = T2S.getVoices();
		if (!voices.length) {
			return;
		}
		utter.voice = voices[voiceIndex];
		let index = 0;
		while (utter.voice.lang.substr(0, 2) != 'en') {
			// find an english voice
			utter.voice = voices[index++];
		}
		// will use default voice first time
		utter.pitch = pitch || 1.5;
		utter.volume = .5;
		utter.rate = rate || 2;
		T2S.cancel();
		T2S.speak(utter);
	},

	showMessage(message) {

		GLOBAL.message = message;
		GLOBAL.messageTimer = new Timer(3);
	},

	drawHealthBar(center, hitPoints, maxHitPoints) {
		// health bar
		if (hitPoints < maxHitPoints) {
			const pos = center.subtract(vec2(maxHitPoints / 24, 0));

			drawRect(center, vec2((maxHitPoints + 2) / 12, 3 / 12), new Color(0, 0, 0));
			drawRect(center, vec2(maxHitPoints  / 12, 1 / 12), new Color(.7, .2, .2));
			for (let i = 0; i < hitPoints; i++) {
				drawRect(pos, vec2(1 / 12), new Color(.4, .7, .2));
				pos.x += 1 / 12;
			}
		}

	},

	musicPlaying: false,

};

var musicDef = [
		[
			// instruments
			[.3, 0, 400], [.1, 0, 220, , .33, , 2]
		],
		[
			// patterns
			[
				[, , 3, , , , , , 3, , 15, , , , , , 3, , , , , , , , 3, , 15, , , , , , , , 2, , , , , , 2, , 14, , , , , , 2, , , , , , , , 2, , 14, , , , , , , ,],
				[1, , 25, , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , 24, , 22, , 20, , 17, , , , , , , , , , , , , , , , , , , , , , , , , ,],
			],
			[
				,//[, , 3, , , , , , 3, , 15, , , , , , 3, , , , , , , , 3, , 15, , , , , , , , 2, , , , , , 2, , 14, , , , , , 2, , , , , , , , 2, , 14, , , , , , , ,],
				[1, , 17, , , , 20, , , , 20, , , , 20, , , , , , , , , , , , , , , , , , , , 17, , , , 18, , 17, , , , , , 18, , , , 17, , , , , , 18, , 17, , , , 15, , , ,],
			]
		],
		[0, 0, 1, 1],
		116
	],

// finish the song
	screenClicked,
	musicPat0 = musicDef[1][0],
	musicPat1 = musicDef[1][1];
musicPat1[0] = musicPat0[0];

// make second song
GLOBAL.music = new Music(musicDef);
// swap instruments
musicDef[0].reverse();

musicPat0.splice(1);
musicPat1.splice(1);
musicDef[3] = 160;
GLOBAL.music2 = new Music(musicDef);

if (typeof speechSynthesis !== 'undefined') {
	speechSynthesis.onvoiceschanged = function () {
		GLOBAL.voices = speechSynthesis.getVoices();
	};
	try {
		GLOBAL.voices = speechSynthesis.getVoices();
	} catch (e) {
		// WebView may not expose voices yet
	}
}

class Button {

	constructor(x, y, tileInfo, onClick) {
		this.x = x;
		this.y = y;
		this.pos = vec2(x, y);
		this.tileInfo = tileInfo;

		this.requiresWood = 0;
		this.requiresStone = 0;
		this.requiresFood = 0;
		this.requiresWorker = 0;
		this.requiresMana = 0;
		this.requiresPop = 0;

		this.clicked = onClick;
	}

	isOver(x, y) {

		const isOver = x > this.pos.x - 1 && x < this.pos.x + 1 && y > this.pos.y - 1 && y < this.pos.y + 1;
		
		if (isOver && !this.enoughMaterial()) {
			// tell user what they need
			GLOBAL.showMessage('NEED\n'
				+ (this.requiresWood ? this.requiresWood + ' WOOD\n' : '')
				+ (this.requiresStone ? this.requiresStone + ' STONE\n' : '')
				+ (this.requiresFood ? this.requiresFood + ' FOOD\n' : '')
				+ (this.requiresWorker ? this.requiresWorker + ' WORKER\n' : '')
				+ (this.requiresMana ? this.requiresMana + ' MANA\n' : '')
				+ (this.requiresPop ? this.requiresPop + ' LIVING SPACE' : ''));
			return isOver;
		}

		isOver && this.clicked();

		return isOver;
	}

	enoughMaterial() {
		return GLOBAL.wood >= this.requiresWood
			&& GLOBAL.stone >= this.requiresStone
			&& GLOBAL.food >= this.requiresFood
			&& GLOBAL.mana >= this.requiresMana
			&& GLOBAL.countWorkers() >= this.requiresWorker
			&& GLOBAL.getSupportedPop() - GLOBAL.units.length >= (this.requiresPop ? this.requiresPop : -1000);
	}


	draw() {

		const color = new Color(1, 1, 1, this.enoughMaterial() ? 1 : .5);

		this.pos = screenToWorld(vec2(this.x, innerHeight - this.y));

		drawTile(
			screenToWorld(vec2(this.x, innerHeight - this.y)),
			vec2(2),
			tile(vec2(0, 72), vec2(24, 24)),
			color
		);
		drawTile(
			screenToWorld(vec2(this.x, innerHeight - this.y)),
			vec2(this.tileInfo.size.x / 12, this.tileInfo.size.y / 12),
			this.tileInfo,
			color
		);
			
	}
}

class Building extends EngineObject {

	constructor(pos, size, tileInfo) {

		// starts as building site
		super(pos, vec2(1), tile(58));

		this.renderOrder = -10000;

		this.popSupport = 0;
		this.hitPoints = 6;
		this.maxHitPoints = 6;

		this.pos = pos;
		this.builtTileInfo = tileInfo;
		this.builtSize = size;

		GLOBAL.mapGrid[pos.y][pos.x] = this;

		this.needsBuilt = 10;

		this.smokePos = 0;

		for (let i = 0; i < GLOBAL.units.length; i++) {
			const unit = GLOBAL.units[i];
			unit.selected && unit.takeOrder('build', this);
		}

	}

	build(amt) {
		this.needsBuilt = max(0, this.needsBuilt - amt);

		if (this.needsBuilt <= 0) {
			this.tileInfo = this.builtTileInfo;
			this.size = this.builtSize;
			this.renderOrder = -this.pos.y;
			return true;
		}

	}

	handleClick(selectedUnits) {
		if (this.needsBuilt) {
			// resume building
			for (let i = 0; i < selectedUnits.length; i++) {
				selectedUnits[i].takeOrder('build', this);
			}

			return true;
		}
	}

	takeDamage(amt) {

		this.hitPoints -= amt;

		if (this.hitPoints <= 0 || this.needsBuilt) {
			this.destroy();
		}
	}
	
	destroy() {

		const index = GLOBAL.buildings.indexOf(this);
		if (index != -1) {
			GLOBAL.buildings.splice(index, 1);
		}
		GLOBAL.mapGrid[Math.round(this.pos.y)][Math.round(this.pos.x)] = 0;

		super.destroy();
	}

	render() {
		super.render();

		GLOBAL.drawHealthBar(this.pos.subtract(vec2(0, 1)), this.hitPoints, this.maxHitPoints);

	}

	update() {
		if (!this.needsBuilt && this.smokePos) {
			// show smoke from chimney
			if (Math.random() < .05) {
				GLOBAL.vfxMan.addParticles(this.smokePos, GLOBAL.vfxMan.smoke, 1);
			}
		}
	}

}


class Unit extends EngineObject {

	constructor(pos, size, tileInfo) {

		super(pos, size, tileInfo);

		this.destination = pos;

		this.hitPoints = 3;
		this.maxHitPoints = 3;
		this.speed = 1 / 48;
		this.walkFrame = 0;
		this.walkTile = tile(tileInfo.pos.add(vec2(12, 0), tileInfo.size));

		this.intention = undefined;
		this.intentionTarget = undefined;

		this.actionTimer = new Timer;
		this.actionFrame = 0;
		this.jumpHeight = 0;

		this.weapon = undefined;
	}

	takeDamage(amt) {

		this.hitPoints -= amt;

		GLOBAL.vfxMan.addParticles(this.pos, GLOBAL.vfxMan.bloodDrops);
		//GLOBAL.speak('ow!', undefined, 2, 2);

		if (this.hitPoints <= 0) {

			this.destroy();
		}

	}

	destroy() {

		const array = this instanceof PlayerUnit ? GLOBAL.units : GLOBAL.enemies;

		const index = array.indexOf(this);
		if (index != -1) {
			array.splice(index, 1);
		}

		super.destroy();
	}

	render() {

		// pre render

		let pos = this.pos.add(vec2(0, this.step ? 1 / 12 : this.jumpHeight));

		if (this.shelter) 
			pos = this.pos.add(vec2(0, 8 / 12));

		this.step = Math.floor(this.walkFrame / 10) % 2;
		// render
		drawTile(
			pos,
			this.size,
			this.step ? this.walkTile : this.tileInfo,
			undefined,
			undefined,
			this.mirror
		);

		// item in hand
		let tilePos;
		if (this.intention == 'chop' || this.weapon == 'axe')
			tilePos = vec2(24);
		else if (this.intention == 'mine')
			tilePos = vec2(48, 24);
		else if (this.intention == 'build')
			tilePos = vec2(72, 24);
		else if (this.intention == 'farm')
			tilePos = vec2(48, 72);
		else if (this.weapon == 'sword')
			tilePos = vec2(72, 48);
		else if (this.weapon == 'spear')
			tilePos = vec2(0, 24);
		else if (this.weapon == 'bow')
			tilePos = vec2(72);

		tilePos && this.drawTool(tilePos);


		!this.shelter && GLOBAL.drawHealthBar(this.pos.subtract(vec2(0, 8/12)), this.hitPoints, this.maxHitPoints);
	}

	drawTool(tilePos) {

		let pos = this.pos.add(vec2(0, this.step ? -3 / 12 : -2 / 12 + this.jumpHeight))
		if (this.shelter)
			pos = this.pos.add(vec2(0, 6 / 12));

		drawTile(
			pos,
			vec2(2),
			tile(tilePos, 24),
			undefined,
			(this.mirror ? 1 : -1) * this.actionFrame / (PI*12),
			this.mirror
		);
	}

	searchAndDestroy(array, range, callback) {

		let closest = Infinity;
		let target;
		for (let i = 0; i < array.length; i++) {
			const unit = array[i];
			const dist = this.pos.distance(unit.pos);
			if (dist < range  && dist < closest) {
				target = unit;
				closest = dist;
			}
		}
		target && callback(target);
	}

}

class PlayerUnit extends Unit {

	constructor(pos, size, tileInfo = tile(4)) {

		super(pos, size, tileInfo);

		this.selected = false;

		this.wood = 0;
		this.stone = 0;
		this.food = 0;

		this.prayTimer = new Timer();
	}

	isOver(x, y) {

		const select = x > this.pos.x - this.size.x / 2 && x < this.pos.x + this.size.x / 2 && y > this.pos.y - this.size.y / 2 && y < this.pos.y + this.size.y / 2;

		if (select && !this.selected) {
			const chance = rand();
			GLOBAL.speak(chance < .3 ? 'what' : chance < .6 ? 'huh?' : 'ready');
		}

		return select;
	}

	takeOrder(order, target) {

		// leave shelter
		this.shelter = undefined;

		this.actionTimer.unset();
		this.prayTimer.unset();
		if (this.oldTileInfo) {
			this.tileInfo = this.oldTileInfo;
			delete this.oldTileInfo;
		}
		this.intention = order;
		this.destination = target ? target.pos : this.pos;
		this.actionFrame = 0;

		if (order != 'move') {
			this.selected = false;
			GLOBAL.state = 0;
		}
		if (order == 'build')
			// move a tiny bit so build starts same tile
			 this.pos = this.pos.add(vec2(.1));

		const possibleSpeak = {
			'chop': ['k', 'choppa', 'yep?'],
			'mine': ['k', 'yep?'],
			'build': ['k', 'hamma?', 'yep?'],
			'store': ['hoard', 'stow', 'store'],
			'shelter': ['shellta', 'safety'],
			'move': ['goin', 'yep?', 'k'],
			'farm': ['fooda', 'grub', 'k'],
			'pray': ['pray', 'holy']
		};

		if (order && possibleSpeak[order]) {
			GLOBAL.speak(possibleSpeak[order][randInt(0, possibleSpeak[order].length)]);
		}

		if (order == 'pray') {
			GLOBAL.showMessage('PRAY TO ME!');
		}

		// reset archer timer
		this.readyFireTimer && this.readyFireTimer.set(5);
	}

	update() {

		if (this.prayTimer.isSet() && this.prayTimer.elapsed()) {
			// animate praying
			if (this.oldTileInfo) {
				this.tileInfo = this.oldTileInfo;
				delete this.oldTileInfo;
			}
			else {
				this.oldTileInfo = this.tileInfo;
				this.tileInfo = tile(3);
			}
			this.prayTimer.set(1);
		
		}

		if (this.actionTimer.isSet()) {
			// performing action

			if (this.actionTimer.elapsed()) {
				this.actionTimer.unset();
				this.jumpHeight = 0;
				if (this.intention == 'chop') {
					// TODO: check if tree still exists
					this.intentionTarget.chop();
					zzfx(...[,.03,405,,,0,3,.1,8,,,,,.1,27,.4,.04,.44,.01]); 
					this.wood += 1;
				}
				else if (this.intention == 'pray') {
					// TODO: check if temple still exists
					const chance = (25 - GLOBAL.mana) / 25;
					if (rand() < chance) {
						zzfx(...[.5, , 600, , , 0, , 3.9, , -1, 650, .05, .04, , , , .08, .84, .18]);
						GLOBAL.mana++;
						GLOBAL.vfxMan.addParticles(this.pos, GLOBAL.vfxMan.manaBalls);
					}
				}
				else if (this.intention == 'farm') {
					// TODO: check if tree still exists
					this.intentionTarget.farm();
					this.food += 1;
				}
				else if (this.intention == 'mine') {
					// TODO: check if stone still exists
					this.intentionTarget.mine();
					zzfx(...[.5,0,1793,,.05,.02,3,.7,,-1,,,,.1,,,,.63,.02,,-1400]);
					this.stone += 1;
				}
				else if (this.intention == 'build') {
					// TODO: check if building still exists
					const built = this.intentionTarget.build(1);
					zzfx(...[, .03, 405, , , 0, 3, .1, 8, , , , , .1, 27, .4, .04, .44, .01]);
					if (built && this.intentionTarget instanceof Building_Farm) {
						this.takeOrder('farm', this.intentionTarget);
					}
					else if (built) {
						// look for work that needs to be done
						for (let i = 0; i < GLOBAL.buildings.length; i++) {
							const building = GLOBAL.buildings[i];
							if (building.needsBuilt) {
								this.takeOrder('build', building);
								return;
							}
						}
			
						// nothing to do
						this.takeOrder();
					}

				}

				if (this.wood + this.stone + this.food>= 3) {
					// return to storage
					this.prevIntention = this.intention;
					this.prevDestination = this.destination;
					this.intention = 'store';
					this.destination = DEFS.HOME;
				}
			}
			else {
				const percent = this.actionTimer.getPercent();
				if (percent > .9) {
					this.actionFrame -= 10;
					this.jumpHeight += percent > .95 ? -1 / 32 : 1 / 32;
				}
				else {
					this.actionFrame++;
				}
			}
		}

		else if (this.destination && (this.destination.x != this.pos.x || this.destination.y != this.pos.y)) {
			
			const angle = this.destination.subtract(this.pos).angle();
			const dist = this.destination.distance(this.pos);

			if (dist < this.speed) {
				// arrived
				this.pos = this.destination;
				this.destination = undefined;
				if (this.intention == 'chop') {
					// look for new target
					let closest = Infinity;
					let target;
					for (let i = 0; i < GLOBAL.trees.length; i++) {
						const tree = GLOBAL.trees[i];
						const dist = tree.pos.subtract(this.pos).length();
						if (dist < closest) {
							target = tree;
							closest = dist;
						}
					}
					if (target) {
						this.destination = target.pos;
					} 
				}
			}
			else {
				// travelling
				const movement = vec2().setAngle(angle, this.speed);
				const newPos = this.pos.add(movement);
				const tileAtPos = GLOBAL.mapMan.getTileAt(newPos);

				if (tileAtPos) {
					// collision
					if (tileAtPos instanceof Tree && this.intention == 'chop') {
						
						this.actionTimer.set(1);
						this.actionFrame = 0;
						this.walkFrame = 0;
						this.intentionTarget = tileAtPos;
					}
					else if (tileAtPos instanceof Stone && this.intention == 'mine') {
						
						this.actionTimer.set(1);
						this.actionFrame = 0;
						this.walkFrame = 0;
						this.intentionTarget = tileAtPos;
					}
					else if (tileAtPos instanceof Building_Temple && this.intention == 'pray') {
						
						this.actionTimer.set(3);
						this.prayTimer.set(.8);
						this.oldTileInfo = this.tileInfo;
						this.tileInfo = tile(3);
						this.actionFrame = 0;
						this.walkFrame = 0;
						this.intentionTarget = tileAtPos;
					}
					else if (tileAtPos instanceof Building && this.intention == 'build' && tileAtPos.needsBuilt) {
						
						this.actionTimer.set(1);
						this.actionFrame = 0;
						this.walkFrame = 0;
						this.intentionTarget = tileAtPos;
					}
					else if (tileAtPos instanceof Building_Farm && this.intention == 'farm') {

						this.actionTimer.set(1);
						this.actionFrame = 0;
						this.walkFrame = 0;
						this.intentionTarget = tileAtPos;
						
					}
					else if (tileAtPos == this.intentionTarget && this.intention == 'shelter') {

						// make sure it is not already occupied
						for (let i = 0; i < GLOBAL.units.length; i++) {
							if (GLOBAL.units[i].shelter == tileAtPos) {
								// already occupied
								this.takeOrder();
								return;
							}
						}
						// shelter in building
						this.pos = tileAtPos.pos.copy();
						this.shelter = tileAtPos;
						this.renderOrder = this.shelter.renderOrder + 1;
						
					}
					else if (tileAtPos instanceof Building_TownHall && this.intention == 'store') {
						
						GLOBAL.wood += this.wood;
						GLOBAL.stone += this.stone;
						GLOBAL.food += this.food;
						this.wood = 0;
						this.stone = 0;
						this.food = 0;
						this.intention = this.prevIntention;
						this.destination = this.prevDestination;
					}
					else {
						// TODO: go around?

						// walk thru for now at half speed
						this.pos = this.pos.add(vec2().setAngle(angle, this.speed / 4));
						this.mirror = movement.x < 0;
						this.walkFrame++;

						this.renderOrder = -this.pos.y;
					}
				}
				else {
					// walk towards destination
					this.pos = newPos;
					this.mirror = movement.x < 0;
					this.walkFrame++;

					this.renderOrder = -this.pos.y;
				}
			}

		}
		else {
			this.walkFrame = 0;
		}

	}
		
	render() {

		// pre render

		// select ring
		this.selected && drawTile(
			this.pos,
			vec2(16 / 12),
			tile(1),
		);

		// render
		super.render();

		// post render
		
	}
}

GLOBAL.inputMan = {

	update() {

		if (mouseWasPressed(2)) {
			// right click, cancel all
			GLOBAL.state = 0;
			GLOBAL.units.forEach((unit) => {
				unit.selected = false;
			});
			GLOBAL.desiredCameraPos = mousePos.copy();
			clearInput();
		}

		if (mouseWasPressed(0)) {

			clearInput();
			screenClicked = true;

			if (!GLOBAL.musicPlaying || !GLOBAL.music.source) {
				GLOBAL.music.playMusic(.6, true);
				GLOBAL.musicPlaying = GLOBAL.music;
			}


			// check UI

			if (GLOBAL.miniMap.isOver(mousePos.x, mousePos.y)) {
				return;
			}

			for (let i = 0; GLOBAL.state == DEFS.STATES.TRAIN_MENU && i < GLOBAL.trainMenu.length; i++) {
				if (GLOBAL.trainMenu[i].isOver(mousePos.x, mousePos.y)) {
					return;
				}
			}
			for (let i = 0; GLOBAL.state == DEFS.STATES.BUILD_MENU && i < GLOBAL.buildMenu.length; i++) {
				if (GLOBAL.buildMenu[i].isOver(mousePos.x, mousePos.y)) {
					return;
				}
			}
			for (let i = 0; GLOBAL.state == DEFS.STATES.TOWNHALL_MENU && i < GLOBAL.townHallMenu.length; i++) {
				if (GLOBAL.townHallMenu[i].isOver(mousePos.x, mousePos.y)) {
					return;
				}
			}
			for (let i = 0; i < GLOBAL.spellMenu.length; i++) {
				if (GLOBAL.spellMenu[i].isOver(mousePos.x, mousePos.y)) {
					return;
				}
			}

			const wereSelected = [];
			let unitClicked = false;
			for (let i = 0; i < GLOBAL.units.length; i++) {
				const unit = GLOBAL.units[i];

				unit.selected && wereSelected.push(unit);
				if (!unitClicked) {
					unitClicked = unit.isOver(mousePos.x, mousePos.y);

					if (unitClicked && !unit.selected) {
						// select unit
						unit.selected = true;

						if (unit.weapon)
							// soldier
							GLOBAL.state = 0;
						else if (unit.shelter && unit.shelter instanceof Building_Barracks)
							// worker in barracks
							GLOBAL.state = DEFS.STATES.TRAIN_MENU;
						else
							// worker
							GLOBAL.state = DEFS.STATES.BUILD_MENU;
					}
				}
			}
			if (unitClicked) {
				// de-select all previously selected units
				wereSelected.forEach((unit) => { unit.selected = false; });
				return;
			}

			// get clicked tile
			let x = max(0, min(35, Math.round(mousePos.x)));
			let y = max(0, min(35, Math.round(mousePos.y)));
			const tile = GLOBAL.mapGrid[y][x];
			
			if (tile) {

				// order selected units to do task
				tile.handleClick && tile.handleClick(wereSelected);
				return;
			}

			if (!wereSelected.length) {
				// start dragging select
				GLOBAL.startSelect = mousePos;
			}


			// this was a move order to selected units
			wereSelected.forEach((unit) => {

				// move command
				unit.takeOrder('move', { pos: mousePos });
				unit.selected = true;
				unit.intention = undefined;
			});
		}

		// select box
		if (GLOBAL.startSelect) {

			if (mouseWasReleased(0)) {
	
				// do selection
				GLOBAL.units.forEach((unit) => {

					unit.selected =
						unit.pos.x > min(GLOBAL.startSelect.x, mousePos.x)
						&& unit.pos.y > min(GLOBAL.startSelect.y, mousePos.y)
						&& unit.pos.x < max(GLOBAL.startSelect.x, mousePos.x)
						&& unit.pos.y < max(GLOBAL.startSelect.y, mousePos.y);
				});

				// end select mode
				delete GLOBAL.startSelect;
			}
			else {
				// draw select box
				const size = mousePos.subtract(GLOBAL.startSelect);
				drawRect(
					mousePos.subtract(size.multiply(vec2(.5))),
					size,
					new Color(1, 1, 1, .2)
				);
			}
		}

	}

};

class MapManager {

	constructor() {
	
		const w = 36;
		const h = 36;
		
		this.mapWidth = w;
		this.mapHeight = h;

		const tileLayer = new TileLayer(vec2(-.5), vec2(w, h), tile(), vec2(1), -1e9);

		for (let y = 0; y < h; y++) 

			for (let x = 0; x < w; x++) {

				// grass
				let tileIndex = 0;
				let rotation = randInt(0, 5);

				const gridValue = GLOBAL.mapGrid[y][x];
				if (gridValue == 'w') 
					tileIndex = 11;

				else if (gridValue == 't') 
					GLOBAL.trees.push(new Tree(vec2(x, y)));

				else if (gridValue == 's') 
					GLOBAL.stones.push(new Stone(vec2(x, y)));

				let info = new TileLayerData(tileIndex, rotation);

				tileLayer.setData(vec2(x, y), info);
			
			}
		

		tileLayer.redraw();

		GLOBAL.buildings.push(
			new Building_TownHall(DEFS.HOME),
			new Building_Temple(vec2(17, 22))
		);

		buildHouse(DEFS.HOME.subtract(vec2(3, 1))).build(10);

		GLOBAL.units.push(new PlayerUnit(vec2(14, 12)));

		GLOBAL.boat = new EngineObject(vec2(33), vec2(3), tile(vec2(48, 132), 24));

		/*let enemy = new Unit_Enemy(vec2(16, 10), 1, tile(6));
		enemy.destination = GLOBAL.buildings[0].pos;
		GLOBAL.enemies.push(
			enemy
		);*/

		
	}

	getTileAt(pos) {

		// handle out of bounds
		if (pos.x < 0 || pos.y < 0)
			return 1;

		return GLOBAL.mapGrid[Math.round(pos.y)][Math.round(pos.x)];

	}


}

class Building_Barracks extends Building {

	constructor(pos) {

		super(pos, vec2(2), tile(vec2(0, 96), vec2(24)));

		this.hitPoints = 10;
		this.maxHitPoints = 10;

		GLOBAL.mapGrid[pos.y][pos.x] = this;

		GLOBAL.wood -= 6;
		GLOBAL.stone -= 4;

	}

	handleClick(selectedUnits) {

		if (super.handleClick(selectedUnits)) {
			return;
		}

		for (let u = 0; u < selectedUnits.length; u++) {
			selectedUnits[u].takeOrder('shelter', this);
			selectedUnits[u].intentionTarget = this;
		}

		if (!selectedUnits.length) {
			GLOBAL.showMessage('PUT A WORKER\nINSIDE TO UPGRADE');
		}


		return true;
	}

	destroy() {
		// check if there are any inhabitants
		for (let i = 0; i < GLOBAL.units.length; i++) {
			const unit = GLOBAL.units[i];

			if (unit.shelter == this) {
				unit.shelter = undefined;
			}
		}

		super.destroy();
	}
}

class Building_Farm extends Building {

	constructor(pos) {

		super(pos, vec2(2), tile(vec2(24, 96), vec2(24)));

		GLOBAL.wood -= 6;
		GLOBAL.stone -= 4;

		this.food = 50;
	}

	handleClick(selectedUnits) {

		if (super.handleClick(selectedUnits)) {
			return;
		}

		for (let u = 0; u < selectedUnits.length; u++) {
			selectedUnits[u].takeOrder('farm', this);
		}


		return true;
	}

	farm() {

		this.food -= 1;

		this.food <= 0 && this.destroy();
	}

}

class Building_Temple extends Building {

	constructor(pos) {

		super(pos, vec2(2), tile(vec2(48, 96), vec2(24)));

		this.hitPoints = 18;
		this.maxHitPoints = 18;
		
		this.build(10);
	}

	handleClick(selectedUnits) {

		if (super.handleClick(selectedUnits)) 
			return;
		
		!selectedUnits.length && GLOBAL.showMessage('HOLY SHRINE');

		for (let u = 0; u < selectedUnits.length; u++) 
			selectedUnits[u].takeOrder('pray', this);
		


		return true;
	}


}

class Building_TownHall extends Building {

	constructor(pos) {

		super(pos, vec2(2), tile(vec2(72, 96), 24));

		this.popSupport = 3;
		this.hitPoints = 18;
		this.maxHitPoints = 18;

		this.build(10);

		this.smokePos = pos.subtract(vec2(.3, -.75));
	}

	handleClick(selectedUnits) {

		for (let u = 0; u < selectedUnits.length; u++) {
			// deselect
			selectedUnits[u].selected = false;
		}

		GLOBAL.state = DEFS.STATES.TOWNHALL_MENU;


		return true;
	}
	

	destroy() {
		// end game
		GLOBAL.state = DEFS.STATES.GAME_LOST;

		super.destroy();
	}

}

class Stone extends EngineObject {

	constructor(pos) {

		super(pos, vec2(1), tile(8));

		this.renderOrder = -pos.y;

		GLOBAL.mapGrid[pos.y][pos.x] = this;

		this.stone = 48;
	}

	handleClick(selectedUnits) {

		selectedUnits.forEach((unit) => {
			if (!unit.weapon) {
				unit.takeOrder('mine', this);
			}
		});
	}

	mine() {

		this.stone -= 1;

		if (this.stone <= 0) {

			GLOBAL.mapGrid[this.pos.y][this.pos.x] = 0;
			GLOBAL.stones.splice(GLOBAL.stones.indexOf(this), 1);
			this.destroy();
		}
	}

}

class Tree extends EngineObject {

	constructor(pos) {

		super(pos.add(vec2(0, .45)), vec2(1, 2), tile(vec2(24, 0), vec2(12, 24)));

		this.renderOrder = -pos.y;
		this.mirror = rand() > .5;

		GLOBAL.mapGrid[pos.y][pos.x] = this;

		this.wood = 8;
	}


	handleClick(selectedUnits) {

		selectedUnits.forEach((unit) => {
			if (!unit.weapon) {
				unit.takeOrder('chop', this);
			}
		});
	}

	chop() {

		this.wood -= 1;

		if (this.wood <= 0) {

			GLOBAL.mapGrid[Math.round(this.pos.y)][this.pos.x] = 0;
			GLOBAL.trees.splice(GLOBAL.trees.indexOf(this), 1);
			this.destroy();
		}
	}

}

class Unit_Archer extends PlayerUnit {

	constructor(pos) {

		super(pos, vec2(1), tile(12));

		this.selected = false;

		this.weapon = 'bow';

		this.readyFireTimer = new Timer(1);

		GLOBAL.wood -= 4;
		GLOBAL.stone -= 2;
	}

	update() {

		if (!this.actionTimer.isSet() && this.readyFireTimer.elapsed()) {
			// look for enemies

			this.searchAndDestroy(GLOBAL.enemies, this.shelter ? 4 : 3, (enemy) => {
				this.actionTimer.unset()
				this.actionFrame = 0;
				this.walkFrame = 0;
				this.intentionTarget = enemy;
				this.intention = 'shoot';
				zzfx(...[.7, , 334, .13, , .2, 4, 3, , , , , , , , , , .77, .03, , 103]);
				this.readyFireTimer.set(4);
				GLOBAL.vfxMan.showArrow(this.pos.copy(), enemy);

			});
		}

		super.update();
	}

}

class Unit_Enemy extends Unit {

	constructor(pos, size, tileInfo, hitPoints) {

		super(pos, size, tileInfo);

		this.weapon = rand() > .5 ? 'axe' : 'sword';

		this.speed = 1 / 64;

		if (hitPoints) {
			this.hitPoints = hitPoints;
			this.maxHitPoints = hitPoints;
		}
	}

	isOver(x, y) {

		return x > this.pos.x - this.size.x / 2 && x < this.pos.x + this.size.x / 2 && y > this.pos.y - this.size.y / 2 && y < this.pos.y + this.size.y / 2;

	}


	update() {

		if (this.actionTimer.isSet()) {
			// performing action

			if (this.actionTimer.elapsed()) {
				this.actionTimer.unset();
				this.jumpHeight = 0;

				// attack
				zzfx(...[, .03, 405, , , 0, 3, .1, 8, , , , , .1, 27, .4, .04, .44, .01]); 
				
				this.intentionTarget.takeDamage(Math.floor(Math.sqrt(this.maxHitPoints)));

			}
			else {
				const percent = this.actionTimer.getPercent();
				if (percent > .9) {
					this.actionFrame -= 10;
					this.jumpHeight += percent > .95 ? -1 / 32 : 1 / 32;
				}
				else {
					this.actionFrame++;
				}
			}
		}

		else if (this.destination && (this.destination.x != this.pos.x || this.destination.y != this.pos.y)) {
			
			const angle = this.destination.subtract(this.pos).angle();
			const dist = this.destination.distance(this.pos);

			if (dist < this.speed) {
				// arrived

				// attack town hall
				this.destination = DEFS.HOME;
			}
			else {

				// look for targets
				this.searchAndDestroy(GLOBAL.units, .8, (enemy) => {
					if (enemy.shelter) return;
					this.actionTimer.set(1);
					this.actionFrame = 0;
					this.intentionTarget = enemy;
				});
				this.searchAndDestroy(GLOBAL.units, 3, (enemy) => {
					this.destination = enemy.pos;
				});
				
				// travelling
				const movement = vec2().setAngle(angle, this.speed);
				const newPos = this.pos.add(movement);
				const tileAtPos = GLOBAL.mapMan.getTileAt(newPos);

				if (tileAtPos) {
					// collision
					if (tileAtPos instanceof Building) {
						
						this.actionTimer.set(1);
						this.actionFrame = 0;
						this.intentionTarget = tileAtPos;
					}
					else {
						// walk thru for now at half speed
						this.pos = this.pos.add(vec2().setAngle(angle, this.speed / 4));
						this.mirror = movement.x < 0;
						this.walkFrame++;

						this.renderOrder = -this.pos.y;
					}
				}
				else {
					// walk towards destination
					this.pos = newPos;
					this.mirror = movement.x < 0;
					this.walkFrame++;

					this.renderOrder = -this.pos.y;
				}
			}

		}
		else {
			this.walkFrame = 0;
		}

	}
		
	render() {

		// pre render

		// render
		super.render();

		// post render

		this.drawTool(this.weapon == 'axe' ? vec2(24) : vec2(72, 48));

	}
}

class Unit_Soldier extends PlayerUnit {

	constructor(pos) {

		super(pos, vec2(1), tile(14));

		this.weapon = 'spear';
		this.hitPoints = 6;
		this.maxHitPoints = 6;

		GLOBAL.wood -= 4;
		GLOBAL.stone -= 2;
	}

	update() {

		if (this.actionTimer.isSet()) {

			if (this.actionTimer.elapsed() && this.intentionTarget instanceof Unit_Enemy) {
				this.actionTimer.unset();
				this.jumpHeight = 0;

				// attack
				zzfx(...[, .03, 405, , , 0, 3, .1, 8, , , , , .1, 27, .4, .04, .44, .01]);
				
				this.intentionTarget.takeDamage(1);
			}
		}
		else if (!this.shelter) {

			// look for targets

			this.searchAndDestroy(GLOBAL.enemies, .8, (enemy) => {
				this.actionTimer.set(1);
				this.actionFrame = 0;
				this.intentionTarget = enemy;
			});
			this.searchAndDestroy(GLOBAL.enemies, 3, (enemy) => {
				this.destination = enemy.pos;
			});
		}

		super.update();

	}

	takeDamage(amt) {
		// armor
		super.takeDamage(amt / 2);
	}
		
}

class Button_Build extends Button {

	constructor(x, y, tileInfo, wood, stone, food, onClick) {

		super(x, y, tileInfo, onClick);

		this.requiresWood = wood;
		this.requiresStone = stone;

	}

}

class Button_CreateWorker extends Button {

	constructor(x, y, tileInfo, onClick) {

		super(x, y, tileInfo, onClick);

		this.requiresPop = 1;

	}

	enoughMaterial() {

		// changes based on pop
		this.requiresFood = 5 * GLOBAL.units.length;
		
		return super.enoughMaterial();
	}

}

class Button_Spell extends Button {

	constructor(x, y, tileInfo, mana, onClick) {

		super(x, y, tileInfo, onClick);

		this.requiresMana = mana;

	}

}

class Button_Train extends Button {

	constructor(x, y, tileInfo, weaponTile, onClick) {

		super(x, y, tileInfo, onClick);

		this.weaponTile = weaponTile;
		this.requiresWood = 4;
		this.requiresStone = 2;
		this.requiresWorker = 1;

	}

	draw() {

		super.draw();

		const color = new Color(1, 1, 1, this.enoughMaterial() ? 1 : .5);

		drawTile(
			screenToWorld(vec2(this.x, innerHeight - this.y)).add(vec2(0, -2/12)),
			vec2(2),
			this.weaponTile,
			color
		);

		// TODO: shield?
	}

}

GLOBAL.miniMap = {
	dx: 128,

	isOver(x, y) {

		const uiPos = screenToWorld(vec2(innerWidth - GLOBAL.miniMap.dx, innerHeight - GLOBAL.miniMap.dx));

		const dx = x - (uiPos.x - 18 / 12);
		const dy = y - (uiPos.y - 18 / 12);

		if (dx > 0 && dx <= 3 && dy > 0 && dy <= 3) {
			GLOBAL.desiredCameraPos = vec2(dx * 12, dy * 12);
			return true;
		}
	},

	draw(dx) {

		GLOBAL.miniMap.dx = dx;

		const uiPos = screenToWorld(vec2(innerWidth - dx, innerHeight - dx));

		drawTile(
			uiPos,
			vec2(3),
			tile(vec2(0, 144), vec2(36, 36))
		);
		drawRect(uiPos, vec2(28 / 12), new Color(.2, .6, .4));

		GLOBAL.miniMap.drawObjects(
			uiPos,
			[
				GLOBAL.trees,
				GLOBAL.stones,
				GLOBAL.buildings.concat(GLOBAL.units),
				GLOBAL.enemies
			],
			[
				new Color(.3, .4, .2),
				new Color(.61, .68, .72),
				new Color(.6, 1, .3),
				new Color(.9, .3, .4)
			]
		);
	},

	drawObjects(uiPos, arrays, colors) {

		for (let a = 0; a < arrays.length; a++) {
			const array = arrays[a];
			const color = colors[a];

			for (let i = 0; i < array.length; i++) {
				const obj = array[i];
				drawRect(
					uiPos.add(vec2((obj.pos.x - 18) / 12, (obj.pos.y - 18) / 12)),
					vec2((obj instanceof Building_TownHall ? 2 : 1) / 12),
					color
				);
			}
		}
	}
}
function gameRenderPost() {
	
	GLOBAL.vfxMan.render();
	
	if (GLOBAL.state == DEFS.STATES.GAME_LOST) {

		GLOBAL.uiFont.drawText(
			'YOU HAVE LOST\nCLICK TO RETRY?',
			screenToWorld(vec2(innerWidth / 2, innerHeight / 2)),
			.08,
			true
		);

		return;
	}
	else if (GLOBAL.state == DEFS.STATES.GAME_WON) {

		cameraScale += GLOBAL.dScale;
		if (cameraScale > GLOBAL.maxCameraScale || cameraScale < GLOBAL.minCameraScale) {
			GLOBAL.dScale = -GLOBAL.dScale;
		}

		GLOBAL.uiFont.drawText(
			'YOU HAVE DEFEATED\nTHE HEROES!',
			screenToWorld(vec2(innerWidth / 2, innerHeight / 2)),
			.08,
			true
		);

		return;
	}

	if (GLOBAL.state > 5) {
		// building mode

		// draw temp structure

		const
			x = Math.round(mousePos.x),
			y = Math.round(mousePos.y);

		let color = new Color(1, 1, 1, .5),
			size = vec2(1),
			tileInfo = tile(50);
		
		if (GLOBAL.mapMan.getTileAt(mousePos)) {
			// illegal position 
			color = new Color(1, 0, 0, .5);
		}


		if (GLOBAL.state == DEFS.STATES.BUILD_BARRACKS) {
			size = vec2(2);
			tileInfo = tile(vec2(0, 96), vec2(24));
		}
		else if (GLOBAL.state == DEFS.STATES.BUILD_WALL) {
			tileInfo = tile(51);
		}
		else if (GLOBAL.state == DEFS.STATES.BUILD_FARM) {
			size = vec2(2);
			tileInfo = tile(vec2(24, 96), vec2(24));
		}
		// placing the building
		drawTile(
			vec2(x, y),
			size,
			tileInfo,
			color
		);

	}

	const dx = min(128, Math.round(128 * innerWidth / 800));

	// wood
	let uiPos = screenToWorld(vec2(dx, 64));

	drawUiBox(uiPos, tile(36), GLOBAL.wood);

	const manaPos = screenToWorld(vec2(innerWidth - dx, 64));;
	// mana
	drawUiBox(manaPos, tile(45), GLOBAL.mana);

	// stone
	uiPos = uiPos.subtract(vec2(0, 2));

	drawUiBox(uiPos, tile(44), GLOBAL.stone);
	

	// food
	uiPos = uiPos.subtract(vec2(0, 2));

	drawUiBox(uiPos, tile(37), GLOBAL.food);



	// population
	uiPos = uiPos.subtract(vec2(0, 2));

	drawUiBox(uiPos, tile(4), GLOBAL.units.length + '/' + GLOBAL.getSupportedPop());


	//  ui menus
	if (GLOBAL.state == DEFS.STATES.BUILD_MENU) {
		for (let i = 0; i < GLOBAL.buildMenu.length; i++) {
			GLOBAL.buildMenu[i].draw();
		}
	}
	if (GLOBAL.state == DEFS.STATES.TRAIN_MENU) {
		for (let i = 0; i < GLOBAL.trainMenu.length; i++) {
			GLOBAL.trainMenu[i].draw();
		}
	}
	else if (GLOBAL.state == DEFS.STATES.TOWNHALL_MENU) {
		for (let i = 0; i < GLOBAL.townHallMenu.length; i++) {
			GLOBAL.townHallMenu[i].draw();
		}
	}

	for (let i = 0; i < GLOBAL.spellMenu.length; i++) {
		GLOBAL.spellMenu[i].draw();
	}

	// minimap
	GLOBAL.miniMap.draw(dx);

	// messages
	if (GLOBAL.message) {
		if (GLOBAL.messageTimer.elapsed()) {
			GLOBAL.message = '';
		}

		// display message
		GLOBAL.uiFont.drawText(
			GLOBAL.message,
			cameraPos.subtract(vec2(0, 5)),
			.08,
			true
		);

	}

	// invasion timer
	const countdown = Math.ceil(-GLOBAL.warriorTimer.valueOf());
	let warriorText;

	if (GLOBAL.warriorIndex < 12 && countdown < 31) {
		const def = DEFS.WARRIORS[GLOBAL.warriorIndex];
		warriorText = def.number + ' ' + countdown + '\n' + def.from;
	}
	else if (GLOBAL.enemies.length) {
		warriorText = DEFS.WARRIORS[GLOBAL.warriorIndex - 1].name + '  \n' + GLOBAL.warriorIndex + '/13';
	}
	warriorText && GLOBAL.uiFont.drawText(
		warriorText,
		screenToWorld(vec2(innerWidth / 2, 24)),
		.08,
		true
	);


	// title
	if (!screenClicked)
		GLOBAL.uiFont.drawText(
			'WENDOL\nVILLAGE',
			screenToWorld(vec2(innerWidth / 2, 1 * innerHeight / 3)),
			min(.16, .16 * innerWidth / 700),
			true
		);
}

function drawUiBox(uiPos, tileInfo, text) {
	
	drawTile(
		uiPos,
		vec2(4, 2),
		tile(vec2(0, 48), vec2(48, 24))
	);
	drawTile(
		uiPos.subtract(vec2(.85, 0)),
		vec2(1),
		tileInfo
	);

	GLOBAL.uiFont.drawText(
		text,
		uiPos.add(vec2(.5, .2)),
		.08,
		true
	);


}

GLOBAL.vfxMan = {

	arrows: [],
	bloodDrops: [],
	gasPlumes: [],
	sparks: [],
	heartPlusses: [],
	manaBalls: [],
	smoke: [],

	showArrow (origin, target) {

		const arrow = new EngineObject(
			origin,
			1,
			tile(9),
			target.pos.subtract(origin).angle()
		);
		GLOBAL.vfxMan.arrows.push({
			object: arrow,
			origin: origin,
			target: target
		});
	},

	update () {
		
		for (let i = 0; i < GLOBAL.vfxMan.arrows.length; i++) {
			const arrow = GLOBAL.vfxMan.arrows[i];

			// travel vector
			const vec = arrow.target.pos.subtract(arrow.origin);

			arrow.object.pos = arrow.object.pos.add(vec.clampLength(.1));

			if (arrow.object.pos.subtract(arrow.target.pos).length() < .1) {
				// arrived
				arrow.target.takeDamage(1);
				zzfx(...[,.03,405,,,0,3,.1,8,,,,,.1,27,.4,.04,.44,.01]);
				arrow.object.destroy();
				GLOBAL.vfxMan.arrows.splice(GLOBAL.vfxMan.arrows.indexOf(arrow), 1);
			}
		}

	},

	render  () {
		
		// blood
		GLOBAL.vfxMan.updateParticles(GLOBAL.vfxMan.bloodDrops, function (drop) {
			drawRect(drop.pos, vec2(1 / 12), new Color(.7, .2, .2));
			// gravity
			drop.dy -= .002;
		});

		// gas
		GLOBAL.vfxMan.updateParticles(GLOBAL.vfxMan.gasPlumes, function (drop) {
			drawRect(drop.pos, vec2(3 / 12), new Color(.4, .7, .2, .4));
			drop.pos.y -= drop.dy / 2;
		});

		// health
		GLOBAL.vfxMan.updateParticles(GLOBAL.vfxMan.heartPlusses, function (drop) {
			drawRect(drop.pos, vec2(3 / 12, 1 / 12), new Color(.4, .7, .2));
			drawRect(drop.pos, vec2(1 / 12, 3 / 12), new Color(.4, .7, .2));
		});


		// sparks
		GLOBAL.vfxMan.updateParticles(GLOBAL.vfxMan.sparks, function (drop) {
			drawRect(drop.pos, vec2(1 / 12), new Color(1, .9, .2));
		});

		// manaballs
		GLOBAL.vfxMan.updateParticles(GLOBAL.vfxMan.manaBalls, function (drop) {
			drawRect(drop.pos, vec2(2 / 12), new Color(.4, .6, 1));
		});

		// smoke
		GLOBAL.vfxMan.updateParticles(GLOBAL.vfxMan.smoke, function (drop) {
			drawRect(drop.pos, vec2(2 / 12), new Color(.6, .7, .7, 1 - drop.lifetime / 40));
			drop.pos.x -= drop.dx / 2;
			drop.pos.y -= 3 * drop.dy / 4;
		});

	},

	updateParticles(array, funcDraw) {
		
		for (let i = 0; i < array.length; i++) {
			const drop = array[i];
			drop.pos.x += drop.dx;
			drop.pos.y += drop.dy;
			funcDraw(drop)
			drop.lifetime++;
			if (drop.lifetime > 40) {
				array.splice(i, 1);
				i--;
			}
		}
	},

	addParticles (pos, array, max=5) {
		
		const drops = randInt(1, max);

		for (let i = 0; i < drops; i++) {
			const angle = rand() * PI;
			array.push({
				pos: pos.copy(),
				dx: .01 * Math.cos(angle),
				dy: .05 * Math.sin(angle),
				lifetime: 0,
				
			})
		}
	}

};

function startWendolGame() {
	if (window.__wendolResizeCanvas) {
		window.__wendolResizeCanvas();
	}
}

window.__wendolTryStart = startWendolGame;

loadMapData(doEngineInit);