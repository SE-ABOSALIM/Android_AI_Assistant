const demos = [
  {
    src: "../assets/demos/app-search.gif",
    alt: "Application search demo",
    title: "Application Search",
    description: "Opening an application and performing an in-app search through voice commands."
  },
  {
    src: "../assets/demos/dynamic-selection.gif",
    alt: "Dynamic UI selection demo",
    title: "Dynamic UI Selection",
    description: "Numbered on-screen candidates help the user choose the correct matching UI element."
  },
  {
    src: "../assets/demos/custom-command.gif",
    alt: "Custom command flow demo",
    title: "Custom Command Flow",
    description: "A saved multi-step workflow runs actions sequentially with voice-triggered execution."
  }
];

let currentIndex = 0;

const media = document.getElementById("demo-media");
const title = document.getElementById("demo-title");
const description = document.getElementById("demo-description");
const kicker = document.getElementById("demo-kicker");
const dots = Array.from(document.querySelectorAll(".dot"));

function renderDemo(index) {
  currentIndex = (index + demos.length) % demos.length;
  const demo = demos[currentIndex];
  media.src = demo.src;
  media.alt = demo.alt;
  title.textContent = demo.title;
  description.textContent = demo.description;
  kicker.textContent = `Demo ${currentIndex + 1} of ${demos.length}`;
  dots.forEach((dot, dotIndex) => {
    const isActive = dotIndex === currentIndex;
    dot.classList.toggle("is-active", isActive);
    dot.setAttribute("aria-selected", String(isActive));
  });
}

document.querySelectorAll("[data-direction]").forEach((button) => {
  button.addEventListener("click", () => {
    const direction = button.dataset.direction === "next" ? 1 : -1;
    renderDemo(currentIndex + direction);
  });
});

dots.forEach((dot) => {
  dot.addEventListener("click", () => renderDemo(Number(dot.dataset.index)));
});

document.addEventListener("keydown", (event) => {
  if (event.key === "ArrowRight") renderDemo(currentIndex + 1);
  if (event.key === "ArrowLeft") renderDemo(currentIndex - 1);
});

let touchStartX = null;
document.querySelector(".phone-frame").addEventListener("touchstart", (event) => {
  touchStartX = event.changedTouches[0].clientX;
}, { passive: true });

document.querySelector(".phone-frame").addEventListener("touchend", (event) => {
  if (touchStartX === null) return;
  const delta = event.changedTouches[0].clientX - touchStartX;
  if (Math.abs(delta) > 42) renderDemo(currentIndex + (delta < 0 ? 1 : -1));
  touchStartX = null;
}, { passive: true });

renderDemo(0);
