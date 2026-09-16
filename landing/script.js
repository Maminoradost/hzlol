const revealItems = document.querySelectorAll('.feature-card, .philosophy, .install');
const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.12 });

revealItems.forEach((item) => observer.observe(item));

const visual = document.querySelector('.hero-visual');
const dashboard = document.querySelector('.dashboard');
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

if (visual && dashboard && !reduceMotion) {
  visual.addEventListener('pointermove', (event) => {
    const bounds = visual.getBoundingClientRect();
    const x = (event.clientX - bounds.left) / bounds.width - 0.5;
    const y = (event.clientY - bounds.top) / bounds.height - 0.5;
    dashboard.style.transform = `rotate(${3 + x * 3}deg) translate(${x * 7}px, ${y * 7}px)`;
  });

  visual.addEventListener('pointerleave', () => {
    dashboard.style.transform = '';
  });
}

if (!reduceMotion) {
  const particleLayer = document.createElement('div');
  particleLayer.className = 'particle-layer';
  document.body.appendChild(particleLayer);

  for (let index = 0; index < 13; index += 1) {
    const particle = document.createElement('span');
    particle.className = 'sakura-particle';
    particle.textContent = index % 3 === 0 ? '✦' : '·';
    particle.style.left = `${4 + Math.random() * 92}%`;
    particle.style.animationDelay = `${Math.random() * -14}s`;
    particle.style.animationDuration = `${10 + Math.random() * 9}s`;
    particle.style.fontSize = `${7 + Math.random() * 11}px`;
    particleLayer.appendChild(particle);
  }
}
