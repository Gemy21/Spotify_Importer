/**
 * spotdl-inspired intelligent matching algorithm for music tracks.
 * Uses title similarity, artist presence, duration tolerance (exponential decay),
 * and modifier/forbidden word filtering (e.g. loops, covers, instrumentals, piano, acoustic, reactions).
 */

export interface CandidateVideo {
  title: string;
  url: string;
  seconds?: number;
  author?: {
    name?: string;
  };
  views?: number;
}

export interface TargetTrack {
  name: string;
  artist: string;
  album?: string;
  durationSec?: number;
}

export const FORBIDDEN_WORDS = [
  'bassboosted',
  'bass boosted',
  'remix',
  'remastered',
  'remaster',
  'reverb',
  'bassboost',
  'live',
  'acoustic',
  'unplugged',
  'pianoforte',
  'piano version',
  'piano',
  'orchestral',
  'orchestra',
  '8d audio',
  '8daudio',
  'concert',
  'acapella',
  'slowed',
  'instrumental',
  'karaoke',
  'cover',
  'tribute',
  'reaction',
  '1 hour',
  '10 hour',
  'extended',
  'loop',
  'clean version',
  'parody',
];

/**
 * Normalizes text to lowercase alphanumeric words.
 */
export function cleanTitle(str: string): string {
  return (str || '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Calculates string similarity using character bigrams / Dice's coefficient (0 to 1).
 */
export function stringSimilarity(a: string, b: string): number {
  const normA = cleanTitle(a);
  const normB = cleanTitle(b);
  if (!normA || !normB) return 0;
  if (normA === normB) return 1.0;

  const getBigrams = (s: string) => {
    const bigrams = new Set<string>();
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
 * SpotDL exponential decay duration matching:
 * diff = abs(target - candidate)
 * Accommodates up to 4s of normal silence padding without penalty.
 */
export function calcTimeMatch(targetSec?: number, candidateSec?: number): number {
  if (!targetSec || !candidateSec) return 70; // Neutral baseline if duration is unknown
  const diff = Math.abs(targetSec - candidateSec);
  if (diff <= 4) return 100;
  return Math.exp(-0.08 * (diff - 4)) * 100;
}

/**
 * Computes a composite match score for a candidate video against target metadata.
 */
export function scoreCandidate(
  candidate: CandidateVideo,
  target: TargetTrack
): { score: number; details: Record<string, number> } {
  const targetTitleNorm = cleanTitle(target.name);
  const candTitleNorm = cleanTitle(candidate.title);
  const candAuthorNorm = cleanTitle(candidate.author?.name || '');
  const targetArtistNorm = cleanTitle(target.artist);

  // 1. Time matching (SpotDL duration formula with silence tolerance)
  const timeScore = calcTimeMatch(target.durationSec, candidate.seconds);

  // 2. Title similarity
  const nameScore = stringSimilarity(target.name, candidate.title) * 100;

  // 3. Artist matching & channel verification (prioritizes Topic & Official channels)
  let artistScore = 0;
  let uploaderBonus = 0;

  const isAuthorArtist = candAuthorNorm.includes(targetArtistNorm);
  const isTopic = candAuthorNorm.includes('topic');
  const isVevo = candAuthorNorm.includes('vevo');

  if (isTopic) {
    artistScore = 100;
    uploaderBonus += 40; // Auto-generated YouTube Music studio audio track
  } else if (isAuthorArtist || isVevo) {
    artistScore = 100;
    uploaderBonus += 30; // Verified artist / VEVO channel
  } else if (candTitleNorm.includes(targetArtistNorm)) {
    artistScore = 70; // Artist name in title, but uploaded by 3rd party
  } else {
    artistScore = stringSimilarity(target.artist, candidate.author?.name || '') * 60;
  }

  // 4. Forbidden / Modifier words check (SpotDL)
  // Only penalize if the target track itself does NOT contain that word
  let forbiddenPenalty = 0;
  for (const word of FORBIDDEN_WORDS) {
    if (candTitleNorm.includes(word) && !targetTitleNorm.includes(word)) {
      forbiddenPenalty += 40;
    }
  }

  // 5. Official Audio bonus
  if (
    (candTitleNorm.includes('official audio') || candTitleNorm.includes('official track')) &&
    forbiddenPenalty === 0
  ) {
    uploaderBonus += 15;
  }

  // 6. Extreme duration mismatch penalty (e.g. 10hr loop, or music video with 1-min skit)
  if (target.durationSec && candidate.seconds) {
    const diff = Math.abs(target.durationSec - candidate.seconds);
    if (diff > 35) {
      forbiddenPenalty += 60;
    }
    if (diff > 90) {
      forbiddenPenalty += 120;
    }
  }

  // 7. Modest view count tiebreaker
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

  return {
    score: finalScore,
    details: {
      nameScore,
      artistScore,
      timeScore,
      uploaderBonus,
      viewsScore,
      forbiddenPenalty,
    },
  };
}

/**
 * Evaluates candidate videos and returns the best matching video.
 */
export function selectBestCandidate(
  candidates: CandidateVideo[],
  target: TargetTrack
): CandidateVideo | null {
  if (!candidates || candidates.length === 0) return null;

  // Deduplicate by URL
  const seen = new Set<string>();
  const unique = candidates.filter((c) => {
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
 * Builds spotdl-style search queries prioritizing official audio and topic tracks.
 */
export function buildSearchQueries(artist: string, name: string, source: string): string[] {
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
