var keys = {};

registerKey("left", [65, 37]);
registerKey("right", [68, 39]);
registerKey("up", [87, 38]);
registerKey("down", [83, 40]);
registerKey("exit", [27]);

function registerKey(key, keyCodes) {
    keys[key] = false;
    document.addEventListener("keydown", (ev) => {
        if (keyCodes.includes(ev.keyCode)) {
            ev.preventDefault();
            keys[key] = true;
        }
    });
    document.addEventListener("keyup", (ev) => {
        if (keyCodes.includes(ev.keyCode)) {
            ev.preventDefault();
            keys[key] = false;
        }
    });
}

function setTouchKey(key, pressed) {
    keys[key] = pressed;
}

function bindTouchButton(button) {
    const key = button.dataset.key;
    if (!key) return;

    const press = (ev) => {
        ev.preventDefault();
        setTouchKey(key, true);
    };
    const release = (ev) => {
        ev.preventDefault();
        setTouchKey(key, false);
    };

    button.addEventListener("touchstart", press, { passive: false });
    button.addEventListener("touchend", release, { passive: false });
    button.addEventListener("touchcancel", release, { passive: false });
    button.addEventListener("mousedown", press);
    button.addEventListener("mouseup", release);
    button.addEventListener("mouseleave", release);
}

window.addEventListener("load", () => {
    document.querySelectorAll(".touch-btn").forEach(bindTouchButton);
});

export function keyIsDown(key) {
    return !!keys[key];
}
