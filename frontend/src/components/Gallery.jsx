import { useEffect, useState } from 'react';
import { mediaUrl } from '../api.js';
import { formatBytes } from '../lib/media.js';

/**
 * The photos and video on a report.
 *
 * Images open into a lightbox; video plays in place. Video is deliberately not
 * autoplayed and carries `preload="metadata"`, so opening a report with three
 * clips does not start three downloads - the browser fetches enough for a
 * poster frame and waits to be asked.
 *
 * Each tile reserves its aspect ratio from the width and height the server
 * measured, which is what stops the discussion below jumping down the page as
 * the images arrive. Those are null for video and for WebP, so there is a
 * square fallback.
 */
export default function Gallery({ items, onRemove, busyId }) {
  const [open, setOpen] = useState(null);

  // Escape closes the lightbox, and the page behind it must not scroll while it
  // is up.
  useEffect(() => {
    if (!open) return undefined;

    function onKey(event) {
      if (event.key === 'Escape') setOpen(null);
    }
    window.addEventListener('keydown', onKey);
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = previous;
    };
  }, [open]);

  if (!items || items.length === 0) return null;

  const photos = items.filter((item) => item.kind === 'IMAGE').length;
  const videos = items.length - photos;

  return (
    <section className="gallery-wrap">
      <h3 className="gallery-head">
        {describe(photos, videos)}
      </h3>

      <ul className="gallery">
        {items.map((item) => (
          <li className="gallery-item" key={item.id}>
            {item.kind === 'VIDEO' ? (
              <video
                className="gallery-media"
                src={mediaUrl(item.url)}
                controls
                preload="metadata"
                playsInline
              />
            ) : (
              <button
                type="button"
                className="gallery-open"
                onClick={() => setOpen(item)}
                aria-label={`View ${item.originalName} full size`}
                style={ratio(item)}
              >
                <img
                  className="gallery-media"
                  src={mediaUrl(item.url)}
                  alt={item.originalName}
                  loading="lazy"
                  width={item.width ?? undefined}
                  height={item.height ?? undefined}
                />
              </button>
            )}

            <div className="gallery-foot">
              <span className="gallery-by">
                {item.kindLabel} from {item.uploadedBy} · {formatBytes(item.byteSize)}
              </span>
              {item.canDelete && onRemove && (
                <button
                  type="button"
                  className="linkish gallery-remove"
                  disabled={busyId === item.id}
                  onClick={() => onRemove(item)}
                >
                  {busyId === item.id ? 'Removing' : 'Remove'}
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>

      {open && (
        <div
          className="overlay"
          role="presentation"
          onClick={(event) => {
            if (event.target === event.currentTarget) setOpen(null);
          }}
        >
          <div className="lightbox" role="dialog" aria-modal="true" aria-label={open.originalName}>
            <img className="lightbox-img" src={mediaUrl(open.url)} alt={open.originalName} />
            <div className="lightbox-foot">
              <span className="lightbox-name">{open.originalName}</span>
              <button type="button" className="btn btn-quiet btn-sm" onClick={() => setOpen(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

/** "2 photos and a video" reads better than "3 attachments". */
function describe(photos, videos) {
  const parts = [];
  if (photos > 0) parts.push(photos === 1 ? '1 photo' : `${photos} photos`);
  if (videos > 0) parts.push(videos === 1 ? '1 video' : `${videos} videos`);
  return parts.join(' and ');
}

/**
 * Holds the tile's shape before the image loads. Without it the browser gives
 * the <img> no height until the bytes arrive and everything below moves.
 */
function ratio(item) {
  if (!item.width || !item.height) return undefined;
  return { aspectRatio: `${item.width} / ${item.height}` };
}
