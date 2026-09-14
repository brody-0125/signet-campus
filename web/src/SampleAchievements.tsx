import { useState } from 'react'
import { Dialog } from './Dialog'

const samples = [
  { name: 'Accessible documents', mark: 'Aa', category: 'CREATE', duration: '45–60 min', summary: 'Make a campus guide easier for everyone to read.', criteria: ['Use a logical heading structure and meaningful link text.', 'Give informative images useful text alternatives.', 'Check reading order and explain what you improved.'], evidence: 'A before-and-after document, an accessibility check report, and a short explanation of the changes.' },
  { name: 'Keyboard-first navigation', mark: '↹', category: 'EXPLORE', duration: '30–45 min', summary: 'Find the barriers a mouse can hide.', criteria: ['Complete a common campus website task using only a keyboard.', 'Record focus visibility, navigation order and any keyboard traps.', 'Describe a reproducible issue and propose a practical correction.'], evidence: 'A short screen recording and a task log showing the keys used, observations, and a suggested fix.' },
  { name: 'Meaningful image descriptions', mark: '◎', category: 'COMMUNICATE', duration: '20–30 min', summary: 'Help the meaning of an image reach more people.', criteria: ['Explain the purpose of three images in their page context.', 'Write concise alternatives for informative images.', 'Identify decorative images and justify leaving their alternatives empty.'], evidence: 'Three annotated examples with the image context, your proposed alternative text, and your reasoning.' },
]

export function SampleAchievements() {
  const [selected, setSelected] = useState<(typeof samples)[number] | null>(null)
  return <>
    <div className="sample-intro"><span className="sample-label">OPEN PREVIEW</span><p>Three small projects. More accessible campus experiences.<br/>Explore sample criteria freely — no account needed.</p></div>
    <div className="sample-grid">{samples.map((sample, index) => <article className="sample-card" key={sample.name}>
      <div className="sample-art" aria-hidden="true"><span className="sample-number">0{index + 1}</span><span className="sample-symbol">{sample.mark}</span><span className="sample-category">{sample.category}</span></div>
      <div className="sample-copy"><p className="sample-meta">Foundation <span aria-hidden="true">·</span> {sample.duration}</p><h3>{sample.name}</h3><p>{sample.summary}</p><button className="button outline" aria-label={`View sample: ${sample.name}`} onClick={() => setSelected(sample)}>View sample <span aria-hidden="true">↗</span></button></div>
    </article>)}</div>
    <p className="sample-disclaimer">Illustrative achievements and time estimates. These examples do not accept submissions or issue credentials.</p>
    {selected && <Dialog title={selected.name} onClose={() => setSelected(null)}>
      <span className="sample-label">SAMPLE ACHIEVEMENT</span>
      <p className="sample-detail-summary">{selected.summary}</p>
      <div className="criteria"><h3>What you will demonstrate</h3><ul className="sample-criteria">{selected.criteria.map(item => <li key={item}>{item}</li>)}</ul></div>
      <div className="criteria"><h3>Sample evidence</h3><p>{selected.evidence}</p></div>
      <div className="sample-review"><h3>From practice to recognition</h3><p>Prepare evidence → Receive reviewer feedback → Earn a badge when approved.</p></div>
      <p className="sample-disclaimer">Preview only. Choose an available achievement in the live catalog to begin real work.</p>
      <div className="form-actions"><button className="button primary" onClick={() => setSelected(null)}>Back to samples</button></div>
    </Dialog>}
  </>
}
