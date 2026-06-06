<?php
namespace App\Service;

class UserAgentParser
{
    public function parse(?string $userAgent): array
    {
        if (!$userAgent) {
            return [
                'browser' => 'Inconnu',
                'os' => 'Inconnu',
                'device' => 'Other',
            ];
        }
        $browser = 'Other';
        $os = 'Other';
        $device = 'Other';

        // Navigateur
        if (preg_match('/Chrome\/([\d.]+)/i', $userAgent)) {
            $browser = 'Chrome ' . $this->getVersion($userAgent, 'Chrome');
        } elseif (preg_match('/Firefox\/([\d.]+)/i', $userAgent)) {
            $browser = 'Firefox ' . $this->getVersion($userAgent, 'Firefox');
        } elseif (preg_match('/Safari\/([\d.]+)/i', $userAgent) && !preg_match('/Chrome/i', $userAgent)) {
            $browser = 'Safari ' . $this->getVersion($userAgent, 'Safari');
        } elseif (preg_match('/Edge\/([\d.]+)/i', $userAgent)) {
            $browser = 'Edge ' . $this->getVersion($userAgent, 'Edge');
        } elseif (preg_match('/MSIE ([\d.]+)/i', $userAgent)) {
            $browser = 'IE ' . $this->getVersion($userAgent, 'MSIE');
        }

        // OS
        if (preg_match('/Windows NT 10.0/i', $userAgent)) {
            $os = 'Windows 10';
        } elseif (preg_match('/Windows NT 6.3/i', $userAgent)) {
            $os = 'Windows 8.1';
        } elseif (preg_match('/Windows NT 6.2/i', $userAgent)) {
            $os = 'Windows 8';
        } elseif (preg_match('/Windows NT 6.1/i', $userAgent)) {
            $os = 'Windows 7';
        } elseif (preg_match('/Mac OS X ([\d_]+)/i', $userAgent, $m)) {
            $os = 'macOS ' . str_replace('_', '.', $m[1]);
        } elseif (preg_match('/Android ([\d.]+)/i', $userAgent, $m)) {
            $os = 'Android ' . $m[1];
        } elseif (preg_match('/iPhone OS ([\d_]+)/i', $userAgent, $m)) {
            $os = 'iOS ' . str_replace('_', '.', $m[1]);
        } elseif (preg_match('/Linux/i', $userAgent)) {
            $os = 'Linux';
        }

        // Appareil
        if (preg_match('/Mobile|iPhone|Android/i', $userAgent)) {
            $device = 'Mobile';
        } elseif (preg_match('/iPad/i', $userAgent)) {
            $device = 'Tablet';
        } elseif (preg_match('/Windows|Macintosh|Linux/i', $userAgent)) {
            $device = 'Desktop';
        }

        return [
            'browser' => $browser,
            'os' => $os,
            'device' => $device,
        ];
    }

    private function getVersion($ua, $name)
    {
        if (preg_match('/' . preg_quote($name) . '\/([\d.]+)/i', $ua, $m)) {
            return $m[1];
        }
        return '';
    }
}
