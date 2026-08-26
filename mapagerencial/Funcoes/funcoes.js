/* 
 * Este arquivo contem as funções genericas utilizadas em todo o sistema.
 *
 * @author Francisco Bruno
 * @version 1.0, 23/10/2009
 */

/*
 * Define qual o relatorio que deve ser invocado e submete o formulário.
 * @param theForm Objeto do formulario que sera submetido
 * @param rel String com o nome do relatório que será processado
 */


function chamaRelatorio(theForm,rel){
   theForm.relatorio.value = rel;
   preparaForm(theForm);
   theForm.submit();
}

/*
 * Converte uma data recebida no formato DDMMAAAA para DD/MM/AAAA
 * @param dt O objeto do form que contem a data
 */
function converteData(dt){
   var dat = dt.value;
   if (dat.length == 8) {
      dat = dat.substr(0,2)+'/'+
            dat.substr(2,2)+'/'+
            dat.substr(4,4);
   }
   dt.value = dat;
}

function validaHora(hora){

   var lRetorno = false;
 
   if (hora.length == 4) {      
      if ('00' + hora.substr(0,2) <= 23) {
         if ('00' + hora.substr(2,2) <= 59) {
            lRetorno = true;
         }
      }
   }
   return lRetorno;
}

function converteHora(hora){
   var eHora = hora.value;

   if (eHora.length > 0) {
      if (validaHora(eHora)) {
         if (eHora.length == 4) {      
            eHora = eHora.substr(0,2) + ':' + eHora.substr(2,2);
         }
         hora.value = eHora;
      }
      else{
         alert('Formato incorreto de hora: ' + hora.value);
      }
   }
}

function setaValor(theForm, listOfFields, listOfValues) {
	aFields = listOfFields.split("|");
	aValues = listOfValues.split("|");
        for (i = 0; i<aFields.length; i++) {
            field = eval("window.opener.document." + theForm + "." + aFields[i]);
            field.value = aValues[i];
        }		
        window.opener.focus();
        self.close();
}

/*
 * Monta a lista de parâmetros e valores que serão repassados para o relatório,
 * esta função exclui da lista de objetos que irão compor os parâmetros os elementos
 * "relatorio", "parametros" e "valores".
 * @param theForm O Objeto do formulário que será submetido para o relatório
 */
function preparaForm(theForm) {
	if (document.all || document.getElementById) {
        theForm.parametros.value = "";
        theForm.valores.value = "";
		for (i = 0; i < theForm.elements.length; i++) {
                   //Monta a lista de parametros e valores
                   if(theForm.elements[i].name != "relatorio" && theForm.elements[i].name != "parametros" && theForm.elements[i].name != "valores") {
                      theForm.parametros.value += theForm.elements[i].name + ",";
                      theForm.valores.value += theForm.elements[i].value + ",";
                   }
		}
	}
}

function checaCampo (pCampo, pPermiteNulo) {
	if ( pCampo.value == '' ) {
		if ( pPermiteNulo == 'S' ) {
		  return true;
	  } else {
		alert ('Este campo obrigatoriamente deve ser preenchido !');
	    pCampo.focus();
	    return false;
	  }
  }
  return true;
}



