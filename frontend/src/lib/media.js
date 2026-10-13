/**
 * What the board accepts as evidence on a report.
 *
 * These numbers are the same ones MediaStore enforces on the server. They are
 * repeated here rather than fetched because the picker has to reject an
 * oversized file before it spends two minutes uploading it - but the server is
 * the copy that matters, and it re-checks everything. If you change one, change
 * both: MediaStore.MAX_IMAGE_BYTES, MAX_VIDEO_BYTES and MAX_PER_ISSUE.
 */
export const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
export const MAX_VIDEO_BYTES = 40 * 1024 * 1024;
export const MAX_PER_ISSUE = 6;

/**
 * The formats the server will store, which is the set a browser can display
 * without a plugin. The server decides by reading the first bytes of the file;
 * this list only drives the file picker's filter and the first-pass check.
 */
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
export const VIDEO_TYPES = ['video/mp4', 'video/webm'];

/** The accept attribute for <input type="file">. */
export const ACCEPT = [...IMAGE_TYPES, ...VIDEO_TYPES].join(',');

export function isImage(type) {
  return typeof type === 'string' && type.startsWith('image/');
}

export function isVideo(type) {
  return typeof type === 'string' && type.startsWith('video/');
}

export function formatBytes(bytes) {
  if (!Number.isFinite(bytes)) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Why this file cannot be attached, or null if it can.
 *
 * Returns a sentence, not a code: it goes straight to the person who picked the
 * file, and "unsupported media type" tells them nothing they can act on.
 */
export function rejectReason(file) {
  const image = isImage(file.type);
  const video = isVideo(file.type);

  if (!image && !video) {
    return `${file.name} is not a photo or a video.`;
  }
  if (image && !IMAGE_TYPES.includes(file.type)) {
    return `${file.name} is an image format this board cannot show. Use JPEG, PNG, GIF or WebP.`;
  }
  if (video && !VIDEO_TYPES.includes(file.type)) {
    return `${file.name} is a video format this board cannot play. Use MP4 or WebM.`;
  }

  const cap = video ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
  if (file.size > cap) {
    return `${file.name} is ${formatBytes(file.size)}. The limit for ${
      video ? 'video' : 'photos'
    } is ${formatBytes(cap)}.`;
  }

  return null;
}
