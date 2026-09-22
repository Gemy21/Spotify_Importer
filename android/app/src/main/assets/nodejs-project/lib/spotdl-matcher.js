/**
 * SpotDL-inspired intelligent matching algorithm — exact port from spotdl-matcher.ts
 * Uses Dice's coefficient, duration decay, forbidden word filtering, and channel boosts.
 */

const FORBIDDEN_WORDS = [
  'bassboosted', 'bass boosted', 'remix', 'remastered', 'remaster',
  'reverb', 'bassboost', 'live', 'acoustic', 'unplugged', 'pianoforte',
  'piano version', 'piano', 'orchestral', 'orchestra', '8d audio', '8daudio',
  'concert', 'acapella', 'slowed', 'instrumental', 'karaoke', 'cover',
  'tribute', 'reaction', '1 hour', '10 hour', 'extended', 'loop',
  'clean version', 'parody',
];

/**
 * Normalize to lowercase alphanumeric words.
 */
function cleanTitle(str) {
  return (str || '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Dice's coefficient string similarity (0 to 1).
 */
function stringSimilarity(a, b) {
  const normA = cleanTitle(a);
  const normB = cleanTitle(b);
  if (!normA || !normB) return 0;
  if (normA === normB) return 1.0;

  const getBigrams = (s) => {
    const bigrams = new Set();
    for (let i = 0; i < s.length - 1; i++) {
      bigrams.add(s.slice(i, i + 2));
    }
    return bigrams;
  };

  const aBigrams = getBigrams(normA);
  const bBigrams = getBigrams(normB);
  let overlap = 0;
  for (const bg of aBigrams) {
    if (bBigrams.has(bg)) overlap++;
  }
  const total = aBigrams.size + bBigrams.size;
  return total === 0 ? 0 : (2.0 * overlap) / total;
}

/**
 * SpotDL exponential decay duration matching.
 * Allows up to 4s of silence padding without penalty.
 */
function calcTimeMatch(targetSec, candidateSec) {
  if (!targetSec || !candidateSec) return 70;
  const diff = Math.abs(targetSec - candidateSec);
  if (diff <= 4) return 100;
  return Math.exp(-0.08 * (diff - 4)) * 100;
}

/**
 * Composite match score for a candidate video against target track metadata.
 */
function scoreCandidate(candidate, target) {
  const targetTitleNorm = cleanTitle(target.name);
  const candTitleNorm = cleanTitle(candidate.title);
  const candAuthorNorm = cleanTitle(candidate.author?.name || '');
  const targetArtistNorm = cleanTitle(target.artist);

  const timeScore = calcTimeMatch(target.durationSec, candidate.seconds);
  const nameScore = stringSimilarity(target.name, candidate.title) * 100;

  let artistScore = 0;
  let uploaderBonus = 0;

  const isAuthorArtist = candAuthorNorm.includes(targetArtistNorm);
  const isTopic = candAuthorNorm.includes('topic');
  const isVevo = candAuthorNorm.includes('vevo');

  if (isTopic) {
    artistScore = 100;
    uploaderBonus += 40;
  } else if (isAuthorArtist || isVevo) {
    artistScore = 100;
    uploaderBonus += 30;
  } else if (candTitleNorm.includes(targetArtistNorm)) {
    artistScore = 70;
  } else {
    artistScore = stringSimilarity(target.artist, candidate.author?.name || '') * 60;
  }

  let forbiddenPenalty = 0;
  for (const word of FORBIDDEN_WORDS) {
    if (candTitleNorm.includes(word) && !targetTitleNorm.includes(word)) {
      forbiddenPenalty += 40;
    }
  }

  if (
    (candTitleNorm.includes('official audio') || candTitleNorm.includes('official track')) &&
    forbiddenPenalty === 0
  ) {
    uploaderBonus += 15;
  }

  if (target.durationSec && candidate.seconds) {
    const diff = Math.abs(target.durationSec - candidate.seconds);
    if (diff > 35) forbiddenPenalty += 60;
    if (diff > 90) forbiddenPenalty += 120;
  }

  let viewsScore = 0;
  if (candidate.views) {
    viewsScore = Math.min(Math.log10(candidate.views) * 1.5, 10);
  }

  const finalScore =
    nameScore * 0.35 +
    artistScore * 0.35 +
    timeScore * 0.30 +
    uploaderBonus +
    viewsScore -
    forbiddenPenalty;

  return { score: finalScore, details: { nameScore, artistScore, timeScore, uploaderBonus, viewsScore, forbiddenPenalty } };
}

/**
 * Selects the best matching video from a list of candidates.
 */
function selectBestCandidate(candidates, target) {
  if (!candidates || candidates.length === 0) return null;

  const seen = new Set();
  const unique = candidates.filter(c => {
    if (!c.url || seen.has(c.url)) return false;
    seen.add(c.url);
    return true;
  });

  if (unique.length === 0) return null;

  let bestCandidate = unique[0];
  let bestScore = -Infinity;

  for (const candidate of unique) {
    const { score } = scoreCandidate(candidate, target);
    if (score > bestScore) {
      bestScore = score;
      bestCandidate = candidate;
    }
  }

  return bestCandidate;
}

/**
 * Builds spotdl-style search queries.
 */
function buildSearchQueries(artist, name, source) {
  const cleanArtist = (artist || '').trim();
  const cleanName = (name || '').trim();

  if (source === 'youtube-music') {
    return [
      `${cleanArtist} - ${cleanName} topic`,
      `${cleanArtist} - ${cleanName} official audio`,
      `${cleanArtist} - ${cleanName}`,
    ];
  }

  return [
    `${cleanArtist} - ${cleanName} (Official Audio)`,
    `${cleanArtist} - ${cleanName} - Topic`,
    `${cleanArtist} - ${cleanName}`,
  ];
}

module.exports = {
  FORBIDDEN_WORDS,
  cleanTitle,
  stringSimilarity,
  calcTimeMatch,
  scoreCandidate,
  selectBestCandidate,
  buildSearchQueries,
};
