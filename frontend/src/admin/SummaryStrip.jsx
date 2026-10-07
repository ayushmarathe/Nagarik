/**
 * The five numbers a moderator opens the dashboard to see.
 *
 * Each one is a pile of work rather than an achievement, which is why "fixed"
 * sits last and quiet while "past its date" is allowed to go red. They are
 * buttons, not statistics: a count you cannot click is a number you have to go
 * and find the rows for yourself.
 */
export default function SummaryStrip({ summary, bucket, overdueOn, onShowBucket, onShowOverdue }) {
  if (!summary) {
    return (
      <div className="adm-strip" aria-hidden="true">
        {Array.from({ length: 5 }, (_, index) => (
          <div className="adm-stat adm-stat-blank" key={index} />
        ))}
      </div>
    );
  }

  const cards = [
    {
      key: 'inbox',
      tone: summary.unacknowledged > 0 ? 'wants' : 'calm',
      count: summary.unacknowledged,
      label: 'Need a date',
      hint: 'Reported, not picked up',
      on: bucket === 'inbox' && !overdueOn,
      go: () => onShowBucket('inbox'),
    },
    {
      key: 'overdue',
      tone: summary.overdue > 0 ? 'bad' : 'calm',
      count: summary.overdue,
      label: 'Past their date',
      hint: 'Promised, still owed',
      on: overdueOn,
      go: onShowOverdue,
    },
    {
      key: 'verifying',
      tone: 'calm',
      count: summary.awaitingVerification,
      label: 'Being checked',
      hint: 'With the people who backed them',
      on: bucket === 'verifying' && !overdueOn,
      go: () => onShowBucket('verifying'),
    },
    {
      key: 'disputed',
      tone: summary.disputed > 0 ? 'warn' : 'calm',
      count: summary.disputed,
      label: 'Disputed',
      hint: 'Residents say it did not hold',
      on: bucket === 'disputed' && !overdueOn,
      go: () => onShowBucket('disputed'),
    },
    {
      key: 'resolved',
      tone: 'good',
      count: summary.resolved,
      label: 'Confirmed fixed',
      hint: 'Checked and closed',
      on: bucket === 'resolved' && !overdueOn,
      go: () => onShowBucket('resolved'),
    },
  ];

  return (
    <div className="adm-strip">
      {cards.map((card) => (
        <button
          key={card.key}
          type="button"
          className={`adm-stat${card.on ? ' is-on' : ''}`}
          data-tone={card.tone}
          aria-pressed={card.on}
          onClick={card.go}
        >
          <span className="adm-stat-count">{card.count}</span>
          <span className="adm-stat-label">{card.label}</span>
          <span className="adm-stat-hint">{card.hint}</span>
        </button>
      ))}
    </div>
  );
}
