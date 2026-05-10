import { Pipe, PipeTransform } from '@angular/core';
import { LanguageService } from './language.service';

@Pipe({
  name: 't',
  standalone: true,
  pure: false
})
export class TranslatePipe implements PipeTransform {
  constructor(private readonly languageService: LanguageService) {}

  transform(key: string, params?: Record<string, string | number>): string {
    return this.languageService.translate(key, params);
  }
}
