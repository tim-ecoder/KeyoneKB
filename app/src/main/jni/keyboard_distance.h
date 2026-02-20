#ifndef KEYBOARD_DISTANCE_H
#define KEYBOARD_DISTANCE_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Keyboard-weighted Damerau-Levenshtein distance.
 *
 * Factors in physical key proximity on hardware keyboards:
 * - Adjacent keys cost 0.5 instead of 1.0 for substitution
 * - Same-row keys cost 0.7
 * - Far keys cost 1.0
 *
 * Supported layouts: "qwerty", "йцукен" (Russian)
 * Returns -1.0 if distance exceeds max_distance.
 */
float kb_weighted_distance(const char *a, int alen,
                           const char *b, int blen,
                           int max_distance,
                           const char *layout);

/*
 * Get the proximity cost of substituting character a with character b
 * on the given keyboard layout. Returns 0.0 - 1.0.
 * 0.0 = same key, 0.3 = adjacent, 0.6 = same row, 1.0 = far
 */
float kb_substitution_cost(unsigned char a, unsigned char b, const char *layout);

#ifdef __cplusplus
}
#endif

#endif /* KEYBOARD_DISTANCE_H */
