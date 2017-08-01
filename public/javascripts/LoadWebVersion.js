var gameInstance = UnityLoader.instantiate("gameContainer", $("#gameContainer").data("buildjson"), {onProgress: UnityProgress});

$(".fullscreen").on("click", function () {
    gameInstance.SetFullscreen(1);
});
