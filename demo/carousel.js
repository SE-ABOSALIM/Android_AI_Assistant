const demos = [
  {
    src: "../assets/demos/app-search.gif",
    alt: "Application search demo",
    title: "Application Search",
    description: "Opening an application and performing an in-app search through voice commands.",
    durationMs: 15375
  },
  {
    src: "../assets/demos/dynamic-selection.gif",
    alt: "Dynamic UI selection demo",
    title: "Dynamic UI Selection",
    description: "Numbered on-screen candidates help the user choose the correct matching UI element.",
    durationMs: 14000
  },
  {
    src: "../assets/demos/custom-command.gif",
    alt: "Custom command flow demo",
    title: "Custom Command Flow",
    description: "A saved multi-step workflow runs actions sequentially with voice-triggered execution.",
    durationMs: 14000
  }
];

let currentIndex = 0;
let loopTimer = null;
let restartCounter = 0;
let isAnimating = false;

const media = document.getElementById("demo-media");
const title = document.getElementById("demo-title");
const description = document.getElementById("demo-description");
const kicker = document.getElementById("demo-kicker");
const dots = Array.from(document.querySelectorAll(".dot"));
const phoneFrame = document.querySelector(".phone-frame");

function mediaSrc(src) {
  restartCounter += 1;
  return `${src}?restart=${restartCounter}`;
}

function setDemoContent(index) {
  currentIndex = (index + demos.length) % demos.length;
  const demo = demos[currentIndex];
  media.src = mediaSrc(demo.src);
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

function clearLoopTimer() {
  if (loopTimer !== null) {
    window.clearTimeout(loopTimer);
    loopTimer = null;
  }
}

function scheduleReplayCue() {
  clearLoopTimer();
  loopTimer = window.setTimeout(() => {
    const demo = demos[currentIndex];
    phoneFrame.classList.add("is-restarting");
    window.setTimeout(() => {
      media.src = mediaSrc(demo.src);
    }, 180);
    window.setTimeout(() => {
      phoneFrame.classList.remove("is-restarting");
      scheduleReplayCue();
    }, 760);
  }, demos[currentIndex].durationMs);
}

function renderDemo(index, direction = 0, skipAnimation = false) {
  const nextIndex = (index + demos.length) % demos.length;
  if (nextIndex === currentIndex && !skipAnimation) return;

  clearLoopTimer();

  if (skipAnimation || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    setDemoContent(nextIndex);
    scheduleReplayCue();
    return;
  }

  if (isAnimating) return;
  isAnimating = true;
  phoneFrame.style.setProperty("--slide-offset", `${direction < 0 ? -18 : 18}px`);
  phoneFrame.classList.add("is-changing");

  window.setTimeout(() => {
    setDemoContent(nextIndex);
    phoneFrame.style.setProperty("--slide-offset", `${direction < 0 ? 18 : -18}px`);
    window.requestAnimationFrame(() => {
      phoneFrame.classList.remove("is-changing");
    });
  }, 180);

  window.setTimeout(() => {
    isAnimating = false;
    scheduleReplayCue();
  }, 460);
}

document.querySelectorAll("[data-direction]").forEach((button) => {
  button.addEventListener("click", () => {
    const direction = button.dataset.direction === "next" ? 1 : -1;
    renderDemo(currentIndex + direction, direction);
  });
});

dots.forEach((dot) => {
  dot.addEventListener("click", () => {
    const nextIndex = Number(dot.dataset.index);
    const direction = nextIndex > currentIndex ? 1 : -1;
    renderDemo(nextIndex, direction);
  });
});

document.addEventListener("keydown", (event) => {
  if (event.key === "ArrowRight") renderDemo(currentIndex + 1, 1);
  if (event.key === "ArrowLeft") renderDemo(currentIndex - 1, -1);
});

let touchStartX = null;
phoneFrame.addEventListener("touchstart", (event) => {
  touchStartX = event.changedTouches[0].clientX;
}, { passive: true });

phoneFrame.addEventListener("touchend", (event) => {
  if (touchStartX === null) return;
  const delta = event.changedTouches[0].clientX - touchStartX;
  if (Math.abs(delta) > 42) {
    const direction = delta < 0 ? 1 : -1;
    renderDemo(currentIndex + direction, direction);
  }
  touchStartX = null;
}, { passive: true });

renderDemo(0, 0, true);
