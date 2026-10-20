import React, { useEffect, useState } from 'react';
import { useTheme } from '@heroui/use-theme';

interface ThemeProviderProps {
  children: React.ReactNode;
}

export const ThemeProvider: React.FC<ThemeProviderProps> = ({ children }) => {
  const { theme, setTheme } = useTheme();

  useEffect(() => {
    const updateThemeClass = (currentTheme: string) => {
      if (currentTheme === 'dark') {
        document.documentElement.classList.add('dark');
        document.documentElement.style.colorScheme = 'dark';
      } else {
        document.documentElement.classList.remove('dark');
        document.documentElement.style.colorScheme = 'light';
      }
    };

    // 优先读取本地持久化主题模式 (light / dark / system)
    const savedTheme = localStorage.getItem('flux_color_mode');
    if (savedTheme === 'light' || savedTheme === 'dark') {
      if (savedTheme !== theme) {
        setTheme(savedTheme);
      }
      updateThemeClass(savedTheme);
    } else {
      const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
      if (systemTheme !== theme) {
        setTheme(systemTheme);
      }
      updateThemeClass(systemTheme);
    }

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleThemeChange = (e: MediaQueryListEvent) => {
      const mode = localStorage.getItem('flux_color_mode');
      if (!mode || mode === 'system') {
        const newTheme = e.matches ? 'dark' : 'light';
        setTheme(newTheme);
        updateThemeClass(newTheme);
      }
    };

    mediaQuery.addEventListener('change', handleThemeChange);
    return () => mediaQuery.removeEventListener('change', handleThemeChange);
  }, [theme, setTheme]);

  return <>{children}</>;
};
