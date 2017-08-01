var gameInstance;
$("#load-game-button").on("click", function () {
    $(this).addClass("started");
    gameInstance = UnityLoader.instantiate("gameContainer", $("#gameContainer").data("buildjson"), {onProgress: UnityProgress});
    var fs = $("#fullscreen-game-button");
    fs.addClass("started");
    fs.on("click", function () {
        gameInstance.SetFullscreen(1);
    });
});

