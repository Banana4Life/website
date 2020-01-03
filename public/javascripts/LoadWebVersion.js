var loadButton = document.getElementById('load-game-button');

loadButton.addEventListener("click", function(e) {
    var gameContainer = document.getElementById('gameContainer');
    loadButton.classList.add("started");
    var gameInstance = UnityLoader.instantiate("gameContainer", gameContainer.dataset.buildjson, {onProgress: UnityProgress});
    var fs = document.getElementById('fullscreen-game-button');
    fs.classList.add('started');
    fs.addEventListener('click', function() {
        gameInstance.SetFullscreen(1);
    });
});

