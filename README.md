# Widget Coins

Widget Android **transparente e sem bordas** que mostra cotações de criptomoedas
(padrão: **BTC, ETH e SOL em USD**) no estilo "lista de interesse": logo da moeda, símbolo e nome
à esquerda; preço em destaque e variação de 24h (▲ verde / ▼ vermelho) à direita.

## Instalar

1. Baixe [`release/WidgetCoins.apk`](release/WidgetCoins.apk) no celular.
2. Abra o arquivo e permita "instalar apps de fontes desconhecidas" se o Android pedir.
3. Na tela inicial: toque e segure um espaço vazio → **Widgets** → **Widget Coins** → arraste para a tela.

## Uso

| Ação | Resultado |
|---|---|
| Ícone ⟳ no cabeçalho | Atualiza agora |
| **Toque e segure** o widget | O launcher mostra as alças de **redimensionamento** e a opção **Configurações/Reconfigurar** |
| Engrenagem no cabeçalho | Abre as opções |
| Ícone do app "Widget Coins" | Opções aplicadas a todos os widgets |

Nas opções você pode:
- **Adicionar moedas** (atalhos para as populares ou busca por qualquer moeda da CoinGecko), remover e reordenar;
- Escolher **tamanho do texto** (automático, que acompanha o redimensionamento, ou fixo de 8 a 40 sp);
- Ajustar a **opacidade do fundo** (0% = totalmente transparente);
- Trocar a cotação (USD, BRL, EUR, GBP, JPY, BTC);
- Mudar o título do widget;
- Mostrar/ocultar variação 24h e o cabeçalho.

Os preços vêm da API pública da [CoinGecko](https://www.coingecko.com/) e são atualizados
automaticamente **a cada 5 minutos** enquanto o celular está em uso (com a tela apagada o
Android adia a atualização até o aparelho acordar, para economizar bateria).

## Compilar

Requer JDK 17+ e Android SDK (platform 35).

```bash
./gradlew assembleRelease
# APK em app/build/outputs/apk/release/app-release.apk
```

O workflow `.github/workflows/build-apk.yml` também gera o APK a cada push (aba Actions → artefato).

> O APK é assinado com uma chave de debug. Se for compilar em outra máquina, desinstale a versão
> anterior antes de instalar a nova (assinaturas diferentes).
