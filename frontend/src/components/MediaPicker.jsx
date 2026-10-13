import { useEffect, useState } from 'react';
import { ACCEPT, MAX_PER_ISSUE, formatBytes, isVideo, rejectReason } from '../lib/media.js';

/**
 * Picking photos and video to go on a report.
 *
 * Holds the chosen File objects and hands them up; it does not upload anything
 * itself. Uploading has to happen after the report exists, because an
 * attachment needs a report id to hang off, and the alternative - posting the
 * text and the files as one multipart request - means a 20MB video and a
 * three-line description share a fate, so a flaky connection loses the typing
 * too.
 *
 * Previews come from object URLs, which have to be revoked by hand: a browser
 * holds the whole file in memory for as long as the URL exists, so leaking them
 * means a person who picks six videos and changes their mind keeps 120MB until
 * they reload.
 */
export default function MediaPicker({ files, onChange, disabled }) {
  const [rejected, setRejected] = useState([]);
  const [previews, setPreviews] = useState([]);

  // One object URL per file, rebuilt whenever the list changes and revoked on
  // the way out.
  useEffect(() => {
    const made = files.map((file) => ({ file, url: URL.createObjectURL(file) }));
    setPreviews(made);
    return () => made.forEach((entry) => URL.revokeObjectURL(entry.url));
  }, [files]);

  function add(event) {
    const picked = Array.from(event.target.files ?? []);
    // Let the same file be picked again after being removed.
    event.target.value = '';
    if (picked.length === 0) return;

    const problems = [];
    const keep = [];

    picked.forEach((file) => {
      const reason = rejectReason(file);
      if (reason) {
        problems.push(reason);
        return;
      }
      // Same name and size twice is a double-pick, not two files.
      const already = [...files, ...keep].some(
        (existing) => existing.name === file.name && existing.size === file.size,
      );
      if (already) {
        problems.push(`${file.name} is already attached.`);
        return;
      }
      keep.push(file);
    });

    const room = MAX_PER_ISSUE - files.length;
    if (keep.length > room) {
      problems.push(
        room === 0
          ? `A report can hold ${MAX_PER_ISSUE} files. Remove one to add another.`
          : `Only ${room} more file${room === 1 ? '' : 's'} will fit on this report.`,
      );
    }

    setRejected(problems);
    if (keep.length > 0) onChange([...files, ...keep.slice(0, Math.max(room, 0))]);
  }

  function remove(index) {
    setRejected([]);
    onChange(files.filter((_, i) => i !== index));
  }

  const full = files.length >= MAX_PER_ISSUE;

  return (
    <div className="field">
      <label htmlFor="media">Photos or video</label>
      <span className="hint" id="media-hint">
        Optional, and public. A photo of the problem is the fastest way to make a report
        undeniable. Up to {MAX_PER_ISSUE} files — photos to 8MB, video to 40MB.
      </span>

      <input
        id="media"
        className="media-input"
        type="file"
        accept={ACCEPT}
        multiple
        disabled={disabled || full}
        aria-describedby="media-hint"
        onChange={add}
      />

      {full && (
        <span className="hint">
          That is {MAX_PER_ISSUE} files. Remove one to swap it for another.
        </span>
      )}

      {rejected.length > 0 && (
        <div className="notice" role="alert">
          {rejected.map((problem) => (
            <p className="media-problem" key={problem}>
              {problem}
            </p>
          ))}
        </div>
      )}

      {previews.length > 0 && (
        <ul className="media-picks">
          {previews.map((entry, index) => (
            <li className="media-pick" key={entry.url}>
              {isVideo(entry.file.type) ? (
                // muted and no controls: this is a thumbnail, not a player.
                <video className="media-pick-thumb" src={entry.url} muted playsInline />
              ) : (
                <img className="media-pick-thumb" src={entry.url} alt="" />
              )}

              <span className="media-pick-meta">
                <span className="media-pick-name">{entry.file.name}</span>
                <span className="media-pick-size">
                  {isVideo(entry.file.type) ? 'Video' : 'Photo'} · {formatBytes(entry.file.size)}
                </span>
              </span>

              <button
                type="button"
                className="btn btn-quiet btn-sm"
                disabled={disabled}
                onClick={() => remove(index)}
                aria-label={`Remove ${entry.file.name}`}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
