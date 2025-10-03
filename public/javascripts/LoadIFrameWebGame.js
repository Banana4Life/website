let loadButton = document.getElementById('load-game-button');

loadButton.addEventListener("click", function(e) {
    e.stopImmediatePropagation();
    let gameContainer = document.getElementById('gameContainer');
    loadButton.classList.add("started");

    let iframe = document.getElementById("gameIFrame")
    iframe.src = gameContainer.dataset.url;
    iframe.style.display = "block";
});

