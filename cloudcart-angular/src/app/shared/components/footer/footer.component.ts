import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './footer.component.html'
})
export class FooterComponent {
  currentYear = new Date().getFullYear();
  techStack = ['Angular 16', 'AWS Lambda', 'DynamoDB', 'SQS', 'LocalStack'];

  trackByTech(index: number, tech: string): string {
    return tech;
  }
}
