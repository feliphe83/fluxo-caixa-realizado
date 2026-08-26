<%-- 
    Document   : index
    Created on : 17/02/2011, 15:26:14
    Author     : Nichael
--%>

<%@page contentType="text/html" pageEncoding="windows-1252"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=windows-1252">
        <link rel="icon" type="image/png" href="../Imagens/logo.png" />
        
        <script src='../Chart/Chart.min.js'></script>
        
        <script type='text/javascript'>
            window.onload = function(){
                var ctx = document.getElementById('GraficoLine').getContext('2d');
                var LineChart = new Chart(ctx).Line(data, options);
                var ctx2 = document.getElementById('GraficoLine2').getContext('2d');
                var LineChart2 = new Chart(ctx2).Line(data, options);
            }
        </script>
        
    <style type="text/css">

    *{
        font-family: calibri;        
    }

    .box {
        margin: 0px auto;
        width: 70%;
    }

    .box-chart {
        width: 50%;
        margin: 0 auto;
        padding: 1 px;
        alignment-adjust: baseline;
        vertical-align: top;
    }

    </style>  
        
    </head>
             
    <body>
        
        <form>
 
             <div class='box-chart'>
                 <canvas id='GraficoLine' style='width:50%;'></canvas>
             </div>
             <div class='box-chart'>
                 <canvas id='GraficoLine2' style='width:50%;'></canvas>
             </div>
             
             <script type='text/javascript'>
                 var options = {
                     responsive:true
                 };

                 var data = {
                     labels: ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'],
                     datasets: [
                         {
                             label: 'Dados primários',
                             fillColor: 'rgba(220,220,220,0.2)',
                             strokeColor: 'rgba(220,220,220,1)',
                             pointColor: 'rgba(220,220,220,1)',
                             pointStrokeColor: '#fff',
                             pointHighlightFill: '#fff',
                             pointHighlightStroke: 'rgba(220,220,220,2)',
                             data: [38, 58, 50, 29, 96, 37, 10, 300, 97, 30, 60, 30]
                         },
                         {
                             label: 'Dados secundários',
                             fillColor: 'rgba(151,187,205,0.2)',
                             strokeColor: 'rgba(151,187,205,1)',
                             pointColor: 'rgba(151,187,205,1)',
                             pointStrokeColor: '#fff',
                             pointHighlightFill: '#fff',
                             pointHighlightStroke: 'rgba(151,187,205,2)',
                             data: [28, 48, 40, 19, 86, 27, 90, 200, 87, 20, 50, 20]
                         }
                     ]
                 };
             </script>
        </form>
    </body>
    
    
</html>
