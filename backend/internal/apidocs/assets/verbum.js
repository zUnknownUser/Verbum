"use strict";
(() => {
  const copy = {
    "pt-BR": {
      skip: "Ir para as rotas",
      contract: "Contrato OpenAPI ↗",
      eyebrow: "DOCUMENTAÇÃO DA API",
      headline: "A Palavra, conectada.",
      intro:
        "Explore as rotas que conectam os apps Verbum à Bíblia, ao conhecimento e à narração.",
      authTitle: "Autenticação",
      auth: "Use Authorize com um ID token do Firebase para as operações protegidas. O token fica apenas nesta página e não é salvo entre visitas.",
      languageTitle: "Português & English",
      language:
        "Envie lang=pt-BR ou Accept-Language: pt-BR. As referências retornadas abrem na tradução escolhida pelo leitor.",
      limitsTitle: "Experimente com controle",
      limits:
        "Try it out executa chamadas reais. IA e áudio respeitam autenticação, limites e orçamento; execuções autorizadas podem consumir sua cota.",
      reference: "REFERÊNCIA",
      routes: "Explore a API",
      hint: "Filtre por tema e abra uma rota para ver parâmetros, respostas e exemplos.",
      footer: "Uma referência compartilhada entre iOS, Android e backend.",
    },
    en: {
      skip: "Skip to endpoints",
      contract: "OpenAPI contract ↗",
      eyebrow: "API DOCUMENTATION",
      headline: "The Word, connected.",
      intro:
        "Explore the endpoints connecting Verbum apps to Scripture, knowledge and narration.",
      authTitle: "Authentication",
      auth: "Use Authorize with a Firebase ID token for protected operations. The token stays on this page and is not saved between visits.",
      languageTitle: "Português & English",
      language:
        "Send lang=en or Accept-Language: en. Returned references open in the reader’s chosen translation.",
      limitsTitle: "Try it with care",
      limits:
        "Try it out makes real requests. AI and audio enforce authentication, limits and budgets; authorized requests may use your quota.",
      reference: "REFERENCE",
      routes: "Explore the API",
      hint: "Filter by topic and open an endpoint for parameters, responses and examples.",
      footer: "One shared reference for iOS, Android and the backend.",
    },
  };
  let language = navigator.language.toLowerCase().startsWith("pt")
    ? "pt-BR"
    : "en";
  let count = 19;
  const renderLanguage = () => {
    document.documentElement.lang = language;
    document.querySelectorAll("[data-copy]").forEach((element) => {
      element.textContent = copy[language][element.dataset.copy];
    });
    document.querySelector("#route-count").textContent =
      `${count} ${language === "pt-BR" ? "rotas" : "endpoints"}`;
    const button = document.querySelector("#language");
    button.textContent = language === "pt-BR" ? "EN" : "PT-BR";
    button.setAttribute(
      "aria-label",
      language === "pt-BR" ? "Switch to English" : "Mudar para português",
    );
  };
  document.querySelector("#language").addEventListener("click", () => {
    language = language === "pt-BR" ? "en" : "pt-BR";
    renderLanguage();
  });
  document.querySelector("#host").textContent = location.host;
  renderLanguage();
  const ui = SwaggerUIBundle({
    url: "/openapi.yaml",
    dom_id: "#swagger-ui",
    deepLinking: true,
    filter: true,
    docExpansion: "none",
    displayRequestDuration: true,
    defaultModelsExpandDepth: -1,
    persistAuthorization: false,
    validatorUrl: null,
    queryConfigEnabled: false,
    tryItOutEnabled: false,
    layout: "BaseLayout",
    onComplete: () => {
      const paths = ui.specSelectors.specJson().toJS().paths || {};
      count = Object.values(paths).reduce(
        (total, path) =>
          total +
          Object.keys(path).filter((method) =>
            [
              "get",
              "post",
              "put",
              "patch",
              "delete",
              "head",
              "options",
            ].includes(method),
          ).length,
        0,
      );
      renderLanguage();
    },
  });
})();
