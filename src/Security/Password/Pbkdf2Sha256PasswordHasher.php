<?php

namespace App\Security\Password;

use Symfony\Component\PasswordHasher\PasswordHasherInterface;

final class Pbkdf2Sha256PasswordHasher implements PasswordHasherInterface
{
    private const FORMAT_PREFIX = 'pbkdf2_sha256';
    private const ITERATIONS = 65536;
    private const SALT_LENGTH_BYTES = 16;
    private const HASH_LENGTH_BYTES = 32;

    public function hash(string $plainPassword): string
    {
        $salt = random_bytes(self::SALT_LENGTH_BYTES);
        $hash = hash_pbkdf2(
            'sha256',
            $plainPassword,
            $salt,
            self::ITERATIONS,
            self::HASH_LENGTH_BYTES,
            true
        );

        return sprintf(
            '%s$%d$%s$%s',
            self::FORMAT_PREFIX,
            self::ITERATIONS,
            base64_encode($salt),
            base64_encode($hash)
        );
    }

    public function verify(string $hashedPassword, string $plainPassword): bool
    {
        if (str_starts_with($hashedPassword, self::FORMAT_PREFIX . '$')) {
            return $this->verifyPbkdf2Hash($hashedPassword, $plainPassword);
        }

        if (preg_match('/^\$2[aby]\$/', $hashedPassword) === 1) {
            return password_verify($plainPassword, $hashedPassword);
        }

        return false;
    }

    public function needsRehash(string $hashedPassword): bool
    {
        if (!str_starts_with($hashedPassword, self::FORMAT_PREFIX . '$')) {
            return true;
        }

        $parts = explode('$', $hashedPassword);
        if (count($parts) !== 4) {
            return true;
        }

        [$prefix, $iterations, $saltB64, $hashB64] = $parts;
        if ($prefix !== self::FORMAT_PREFIX || !ctype_digit($iterations) || (int) $iterations !== self::ITERATIONS) {
            return true;
        }

        $salt = base64_decode($saltB64, true);
        $hash = base64_decode($hashB64, true);

        if ($salt === false || $hash === false) {
            return true;
        }

        return strlen($salt) !== self::SALT_LENGTH_BYTES || strlen($hash) !== self::HASH_LENGTH_BYTES;
    }

    private function verifyPbkdf2Hash(string $hashedPassword, string $plainPassword): bool
    {
        $parts = explode('$', $hashedPassword);
        if (count($parts) !== 4) {
            return false;
        }

        [, $iterations, $saltB64, $hashB64] = $parts;
        if (!ctype_digit($iterations)) {
            return false;
        }

        $salt = base64_decode($saltB64, true);
        $storedHash = base64_decode($hashB64, true);

        if ($salt === false || $storedHash === false) {
            return false;
        }

        $computedHash = hash_pbkdf2(
            'sha256',
            $plainPassword,
            $salt,
            (int) $iterations,
            strlen($storedHash),
            true
        );

        return hash_equals($storedHash, $computedHash);
    }
}