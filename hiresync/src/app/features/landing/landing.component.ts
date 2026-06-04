import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterModule, MatButtonModule, MatIconModule],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss'],
})
export class LandingComponent {
  features = [
    { icon: 'travel_explore', title: 'Scraping Multi-Sources', desc: 'Collecte automatique des offres depuis recrute.ma, rekrut.ma, LinkedIn et des dizaines de portails RH marocains en temps réel.' },
    { icon: 'auto_awesome', title: 'Optimisation IA (LLM)', desc: 'Notre IA (LLaMA 3.1 / Mistral 7B via OpenRouter) réécrit votre CV pour maximiser votre score ATS — de 62% à 88% en moyenne.' },
    { icon: 'hub', title: 'Matching Sémantique', desc: 'Algorithme hybride TF-IDF + embeddings qui calcule un score de correspondance précis entre votre profil et chaque offre.' },
  ];

  steps = [
    { num: '01', icon: 'travel_explore', title: 'Scraping',      desc: 'Agrégation automatique des offres du marché marocain' },
    { num: '02', icon: 'hub',            title: 'Matching',       desc: 'Score de correspondance profil / offre en temps réel' },
    { num: '03', icon: 'auto_awesome',   title: 'Optimisation',   desc: 'Réécriture IA du CV pour l\'offre cible' },
    { num: '04', icon: 'send',           title: 'Candidature',    desc: 'Postulation directe depuis la plateforme' },
    { num: '05', icon: 'track_changes',  title: 'Suivi',          desc: 'Tableau de bord centralisé de vos candidatures' },
  ];
}
